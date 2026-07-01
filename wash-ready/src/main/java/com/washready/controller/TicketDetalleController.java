package com.washready.controller;

import com.washready.model.TicketDetalle;
import com.washready.repository.ServicioRepository;
import com.washready.repository.TicketDetalleRepository;
import com.washready.repository.TicketRepository;
import com.washready.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/detalles")
public class TicketDetalleController {

    private final TicketRepository ticketRepo;
    private final TicketDetalleRepository detalleRepo;
    private final ServicioRepository servicioRepo;
    private final TicketService ticketService;

    public TicketDetalleController(TicketRepository ticketRepo,
                                   TicketDetalleRepository detalleRepo,
                                   ServicioRepository servicioRepo,
                                   TicketService ticketService) {
        this.ticketRepo = ticketRepo;
        this.detalleRepo = detalleRepo;
        this.servicioRepo = servicioRepo;
        this.ticketService = ticketService;
    }


    public record DetalleUpsert(Long servicioId, Integer cantidad, BigDecimal precio, String descripcion) {}

    // DTO de salida
    public record DetalleDto(
            Long id,
            Long servicioId,
            String descripcion,       // SIEMPRE sale del snapshot
            Integer cantidad,
            BigDecimal precio,
            BigDecimal precioTicket,
            BigDecimal importe,
            boolean importeCeroEnTicket
    ) {
        static DetalleDto from(TicketDetalle d) {
            var servicio = d.getServicio();


            String desc = (d.getDescripcionServicio() != null && !d.getDescripcionServicio().isBlank())
                    ? d.getDescripcionServicio()
                    : (servicio != null && servicio.getDescripcion() != null && !servicio.getDescripcion().isBlank()
                        ? servicio.getDescripcion()
                        : ("Línea " + (d.getId() != null ? "#" + d.getId() : "")).trim());

            int qty = (d.getCantidad() != null && d.getCantidad() > 0) ? d.getCantidad() : 1;

            BigDecimal unit = (d.getPrecio() != null) ? d.getPrecio() : BigDecimal.ZERO;

            BigDecimal unitTicket = d.getPrecioParaTicket();
            BigDecimal imp = unit.multiply(BigDecimal.valueOf(qty));

            Long servicioId = (servicio != null ? servicio.getId() : null);
            boolean importeCeroEnTicket = servicio != null && servicio.isImporteCeroEnTicket();

            return new DetalleDto(
                    d.getId(),
                    servicioId,
                    desc,
                    qty,
                    unit,
                    unitTicket,
                    imp,
                    importeCeroEnTicket
            );
        }
    }

    // ===== Listar detalles del ticket =====
    @GetMapping
    public ResponseEntity<List<DetalleDto>> listar(@PathVariable Long ticketId) {
        if (!ticketRepo.existsById(ticketId)) return ResponseEntity.notFound().build();
        var list = detalleRepo.findByTicketId(ticketId).stream().map(DetalleDto::from).toList();
        return ResponseEntity.ok(list);
    }

    // ===== Crear detalle =====
    @PostMapping
    public ResponseEntity<?> crear(@PathVariable Long ticketId, @RequestBody DetalleUpsert req) {
        var ticket = ticketRepo.findById(ticketId).orElse(null);
        if (ticket == null) return ResponseEntity.notFound().build();

        try {
            int qty = (req.cantidad() != null && req.cantidad() > 0) ? req.cantidad() : 1;

            var d = new TicketDetalle();
            d.setTicket(ticket);
            d.setCantidad(qty);

            if (req.servicioId() != null) {
                var servicio = servicioRepo.findById(req.servicioId())
                        .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
                d.setServicio(servicio);

                BigDecimal unit = (req.precio() != null)
                        ? req.precio()
                        : (servicio.getImporte() != null ? servicio.getImporte() : BigDecimal.ZERO);
                d.setPrecio(unit);

                // snapshot: si viene descripcion, se usa; si no, se copia del servicio
                String desc = (req.descripcion() != null && !req.descripcion().isBlank())
                        ? req.descripcion()
                        : servicio.getDescripcion();

                if (desc == null || desc.isBlank()) {
                    throw new IllegalArgumentException("El servicio no tiene descripción y no se ha enviado 'descripcion'");
                }
                d.setDescripcionServicio(desc);

            } else {
                // Línea manual: descripción + precio obligatorios
                if (req.descripcion() == null || req.descripcion().isBlank())
                    throw new IllegalArgumentException("La descripción es obligatoria si no hay servicioId");
                if (req.precio() == null)
                    throw new IllegalArgumentException("El precio es obligatorio si no hay servicioId");

                d.setServicio(null);
                d.setDescripcionServicio(req.descripcion());
                d.setPrecio(req.precio());
            }

            detalleRepo.save(d);
            ticketService.recomputarTotales(ticket.getId());
            return ResponseEntity.status(HttpStatus.CREATED).build();

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    // ===== Actualizar detalle =====
    @PutMapping("/{detalleId}")
    public ResponseEntity<?> actualizar(@PathVariable Long ticketId,
                                        @PathVariable Long detalleId,
                                        @RequestBody DetalleUpsert req) {
        var detOpt = detalleRepo.findByIdAndTicketId(detalleId, ticketId);
        if (detOpt.isEmpty()) return ResponseEntity.notFound().build();

        var d = detOpt.get();

        try {
            // Cambiar servicio (si se envía)
            if (req.servicioId() != null) {
                var servicio = servicioRepo.findById(req.servicioId())
                        .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
                d.setServicio(servicio);

                // snapshot coherente con el nuevo servicio (salvo que te manden una descripción manual)
                String desc = (req.descripcion() != null && !req.descripcion().isBlank())
                        ? req.descripcion()
                        : servicio.getDescripcion();

                if (desc == null || desc.isBlank()) {
                    throw new IllegalArgumentException("El servicio no tiene descripción y no se ha enviado 'descripcion'");
                }
                d.setDescripcionServicio(desc);

                // Si no llega precio y la línea no tiene precio válido, set por defecto del servicio
                if (req.precio() == null && (d.getPrecio() == null || d.getPrecio().compareTo(BigDecimal.ZERO) == 0)) {
                    d.setPrecio(servicio.getImporte() != null ? servicio.getImporte() : BigDecimal.ZERO);
                }
            } else if (req.descripcion() != null) {
                // No cambias servicio, pero sí quieres editar el snapshot
                if (req.descripcion().isBlank())
                    throw new IllegalArgumentException("La descripción no puede ir vacía");
                d.setDescripcionServicio(req.descripcion());
            }

            if (req.cantidad() != null) d.setCantidad(req.cantidad() > 0 ? req.cantidad() : 1);
            if (req.precio() != null) d.setPrecio(req.precio());

            // Si la línea queda sin servicio (porque ya estaba NULL por borrado), asegúrate de que hay snapshot
            if (d.getServicio() == null && (d.getDescripcionServicio() == null || d.getDescripcionServicio().isBlank())) {
                throw new IllegalArgumentException("La línea sin servicio debe tener 'descripcion' informada");
            }

            detalleRepo.save(d);
            ticketService.recomputarTotales(ticketId);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    // ===== Eliminar detalle =====
    @DeleteMapping("/{detalleId}")
    public ResponseEntity<?> eliminar(@PathVariable Long ticketId, @PathVariable Long detalleId) {
        var detOpt = detalleRepo.findByIdAndTicketId(detalleId, ticketId);
        if (detOpt.isEmpty()) return ResponseEntity.notFound().build();

        detalleRepo.delete(detOpt.get());
        ticketService.recomputarTotales(ticketId);
        return ResponseEntity.noContent().build();
    }
}
