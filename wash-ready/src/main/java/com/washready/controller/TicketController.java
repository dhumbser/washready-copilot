package com.washready.controller;

import com.washready.model.Servicio;
import com.washready.model.Ticket;
import com.washready.model.Ticket.Estado;
import com.washready.model.TicketDetalle;
import com.washready.service.TicketService;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import com.washready.util.FechasUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;
    public TicketController(TicketService service) {
        this.service = service;
    }

    private static boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    // ====== DTOs ======
    public record DetalleReq(Long servicioId, Integer cantidad, BigDecimal precio, String descripcion) {
    }

    public record TicketUpsert(
            Instant fecha,
            String referencia,
            String comentarios,
            String bonoMotivo,
            String plaza,
            Long usuarioId,
            Long clienteId,
            Long vehiculoId,
            Ticket.Estado estado,
            Ticket.MetodoPago metodoPago,
            BigDecimal totalSinIva,
            BigDecimal iva,
            BigDecimal total,
            List<DetalleReq> detalles) {
    }

    /** DTO principal **/
    public record TicketDto(
            Long id,
            Instant fecha,
            String referencia,
            String comentarios,
            String bonoMotivo,
            String plaza,
            Long usuarioId,
            Long clienteId,
            Long vehiculoId,
            Ticket.Estado estado,
            Ticket.MetodoPago metodoPago,
            BigDecimal totalSinIva,
            BigDecimal iva,
            BigDecimal total,
            LocalDateTime tms) {
        static TicketDto from(Ticket t) {
            return new TicketDto(
                    t.getId(),
                    t.getFecha(),
                    t.getReferencia(),
                    t.getComentarios(),
                    t.getBonoMotivo(),
                    t.getPlaza(),
                    t.getUsuario() != null ? t.getUsuario().getId() : null,
                    t.getCliente() != null ? t.getCliente().getId() : null,
                    t.getVehiculo() != null ? t.getVehiculo().getId() : null,
                    t.getEstado(),
                    t.getMetodoPago(),
                    t.getTotalSinIva(),
                    t.getIva(),
                    t.getTotal(),
                    t.getTms());
        }
    }

    // DTO de fila para la tabla
    public record TicketRowDto(
            Long id,
            Instant fecha,
            String referencia,
            String matricula,
            String marca,
            String modelo,
            String color,
            String tipoServicio,
            String cliente,
            String clienteTelefono,
            Boolean clienteNoDeseado,
            String centro,
            String operario,
            Estado estado,
            Ticket.MetodoPago metodoPago,
            BigDecimal totalSinIva,
            BigDecimal iva,
            BigDecimal total) {
        private static String formatTipo(Servicio.Tipo tipo) {
            if (tipo == null)
                return null;
            return switch (tipo) {
                case TIPO_1 -> "Tipo 1";
                case TIPO_2 -> "Tipo 2";
                case TIPO_3 -> "Tipo 3";
                case GENERAL -> "General";
            };
        }

        public static TicketRowDto from(Ticket t) {
            var v = t.getVehiculo();
            var c = t.getCliente();
            var u = t.getUsuario();

            String tipoServicio = null;
            try {
                var tipos = t.getDetalles() == null ? List.of()
                        : t.getDetalles().stream()
                                .map(TicketDetalle::getServicio)
                                .filter(java.util.Objects::nonNull)
                                .map(Servicio::getTipo)
                                .map(TicketRowDto::formatTipo)
                                .filter(s -> s != null && !s.isBlank())
                                .distinct()
                                .toList();
                if (!tipos.isEmpty())
                    tipoServicio = String.join(", ", tipos.toArray(new String[0]));
            } catch (Exception ignored) {
            }

            String clienteNombre = (c == null) ? null
                    : ((c.getNombre() == null ? "" : c.getNombre()) + " "
                            + (c.getApellido() == null ? "" : c.getApellido())).trim();
            Boolean noDeseado = (c != null && c.isNoDeseado());
            String telefono = (c != null) ? c.getTelefono() : null;

            String centroNombre = null;
            try {
                if (t.getCentro() != null && t.getCentro().getNombre() != null) {
                    centroNombre = t.getCentro().getNombre();
                } else if (u != null && u.getCentroTrabajo() != null && u.getCentroTrabajo().getNombre() != null) {
                    centroNombre = u.getCentroTrabajo().getNombre();
                }
            } catch (Exception ignored) {
            }

            return new TicketRowDto(
                    t.getId(),
                    t.getFecha(),
                    t.getReferencia(),
                    v != null ? v.getMatricula() : null,
                    v != null ? v.getMarca() : null,
                    v != null ? v.getModelo() : null,
                    v != null ? v.getColor() : null,
                    tipoServicio,
                    clienteNombre,
                    telefono,
                    noDeseado,
                    (centroNombre != null && !centroNombre.isBlank()) ? centroNombre : null,
                    u != null ? u.getUsuario() : null,
                    t.getEstado(),
                    t.getMetodoPago(),
                    t.getTotalSinIva(),
                    t.getIva(),
                    t.getTotal());
        }
    }

    private static Instant startOf(String d) {
        try {
            return LocalDate.parse(d.trim()).atStartOfDay(FechasUtil.ZONE).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant endOf(String d) {
        try {
            return LocalDate.parse(d.trim()).atTime(23, 59, 59).atZone(FechasUtil.ZONE).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ====== Endpoints ======
    @GetMapping
    public List<TicketDto> listar() {
        return service.listar().stream().map(TicketDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDto> obtener(@PathVariable Long id) {
        return service.obtener(id).map(t -> ResponseEntity.ok(TicketDto.from(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody TicketUpsert req,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            boolean admin = isAdmin(jwt);

            var t = new Ticket();
            t.setFecha(req.fecha());
            t.setComentarios(req.comentarios());
            t.setBonoMotivo(req.bonoMotivo());
            t.setPlaza(req.plaza());
            t.setEstado(req.estado());
            t.setMetodoPago(req.metodoPago());
            t.setTotalSinIva(req.totalSinIva());
            if (req.iva() != null)
                t.setIva(req.iva());
            if (req.total() != null)
                t.setTotal(req.total());

            var comandos = (req.detalles() == null ? List.<TicketService.DetalleCommand>of()
                    : req.detalles().stream()
                            .map(d -> new TicketService.DetalleCommand(d.servicioId(), d.cantidad(), d.precio(),
                                    d.descripcion()))
                            .toList());

            // OJO: esto requiere que tu TicketService tenga el parámetro "admin"
            var creado = service.crear(t, req.usuarioId(), req.clienteId(), req.vehiculoId(), comandos, admin);

            return ResponseEntity.status(HttpStatus.CREATED).body(TicketDto.from(creado));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
            @RequestBody TicketUpsert req,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            boolean admin = isAdmin(jwt);

            var data = new Ticket();
            data.setFecha(req.fecha());
            data.setComentarios(req.comentarios());
            data.setBonoMotivo(req.bonoMotivo());
            data.setPlaza(req.plaza());
            data.setEstado(req.estado());
            data.setMetodoPago(req.metodoPago());
            data.setTotalSinIva(req.totalSinIva());
            if (req.iva() != null)
                data.setIva(req.iva());
            if (req.total() != null)
                data.setTotal(req.total());

            // Si detalles es null > no tocar líneas; Si viene lista > reemplazo completo
            var comandos = (req.detalles() == null ? null
                    : req.detalles().stream()
                            .map(d -> new TicketService.DetalleCommand(d.servicioId(), d.cantidad(), d.precio(),
                                    d.descripcion()))
                            .toList());

            return service
                    .actualizarConDetalles(id, data, req.usuarioId(), req.clienteId(), req.vehiculoId(), comandos,
                            admin)
                    .map(t -> ResponseEntity.ok(TicketDto.from(t)))
                    .orElse(ResponseEntity.notFound().build());

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo ADMIN puede eliminar");
        }
        return service.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ====== Marcar como pagado ======
    public record PagarReq(Ticket.MetodoPago metodoPago, String bonoMotivo) {
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> pagar(@PathVariable Long id, @RequestBody PagarReq req, @AuthenticationPrincipal Jwt jwt) {
        try {
            if (req == null || req.metodoPago() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Método de pago requerido");
            }
            boolean admin = isAdmin(jwt);
            return service.marcarComoPagado(id, req.metodoPago(), req.bonoMotivo(), admin)
                    .map(t -> ResponseEntity.ok(TicketDto.from(t)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    // ====== Página/tabla con filtros ======
    @Transactional(readOnly = true)
    @GetMapping("/page")
    public Page<TicketRowDto> page(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String color,
            @RequestParam(required = false, name = "cliente") String clienteNomApe,
            @RequestParam(required = false, name = "telefono") String clienteTelefono,
            @RequestParam(required = false) String operario,
            @RequestParam(required = false) String referencia,
            @RequestParam(required = false) List<Estado> estados,
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false, name = "fdesde") String fdesde,
            @RequestParam(required = false, name = "fhasta") String fhasta,
            @RequestParam(required = false) Long centroId,
            @RequestParam(required = false, name = "metodoPago") Ticket.MetodoPago metodoPago,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        boolean admin = isAdmin(jwt);

        Long centroFiltro = null;
        if (admin) {
            centroFiltro = centroId;
        } else if (jwt != null) {
            Object claim = jwt.getClaim("centroId");
            if (claim != null) {
                try {
                    centroFiltro = Long.valueOf(String.valueOf(claim));
                } catch (Exception ignored) {
                }
            }
        }

        var sort = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"));
        var pageable = PageRequest.of(Math.max(0, page), clamp(size, 1, 10_000), sort);

        // ==== Fechas: si NO se envían, es null (busqueda HISTORICA) ====
        Instant desde = (fdesde != null && !fdesde.isBlank()) ? startOf(fdesde) : null;
        Instant hasta = (fhasta != null && !fhasta.isBlank()) ? endOf(fhasta) : null;

        Page<Ticket> p = service.buscar(
                centroFiltro,
                matricula, marca, modelo, color,
                clienteNomApe, clienteTelefono,
                operario, referencia,
                (estados != null && !estados.isEmpty()) ? estados : null, estado,
                desde, hasta,
                metodoPago,
                pageable);

        return p.map(TicketRowDto::from);
    }

    @GetMapping("/next-ref")
    public ResponseEntity<?> nextRef(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String fecha) {
        if (jwt == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Long centroId = null;
        Object claim = jwt.getClaim("centroId");
        if (claim != null) {
            try {
                centroId = Long.valueOf(String.valueOf(claim));
            } catch (Exception ignored) {
            }
        }
        if (centroId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("centroId no presente en el token");
        }

        Instant f = parseInstant(fecha);

        try {
            String ref = service.nextReferencia(centroId, f);
            return ResponseEntity.ok(java.util.Map.of("referencia", ref));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/{id}/entregar")
    public ResponseEntity<?> entregar(@PathVariable Long id) {
        try {
            return service.marcarComoEntregado(id)
                    .map(t -> ResponseEntity.ok(TicketDto.from(t)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @GetMapping(value = "/{id}/print")
    public ResponseEntity<?> print(@PathVariable Long id, Locale locale) {
        byte[] pdf;
        try {
            pdf = service.printPdf(id, locale);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error al generar el PDF: " + ex.getMessage());
        }

        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No se pudo generar el PDF");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("ticket-" + id + ".pdf").build());
        headers.setCacheControl(CacheControl.noStore());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(FechasUtil.ZONE).toInstant();
        } catch (Exception ignored) {
        }
        return null;
    }

    // ====== Historial de Cliente ======
    public record TicketHistoryDto(
            Long id,
            Instant fecha,
            String referencia,
            BigDecimal total,
            String matricula,
            List<HistoryDetailDto> detalles) {
        public record HistoryDetailDto(String descripcion, BigDecimal precio) {
        }

        static TicketHistoryDto from(Ticket t) {
            var dets = (t.getDetalles() == null) ? List.<HistoryDetailDto>of()
                    : t.getDetalles().stream()
                            .map(d -> new HistoryDetailDto(d.getDescripcionServicio(), d.getPrecio()))
                            .toList();
            String mat = (t.getVehiculo() != null) ? t.getVehiculo().getMatricula() : null;
            return new TicketHistoryDto(t.getId(), t.getFecha(), t.getReferencia(), t.getTotal(), mat, dets);
        }
    }

    @GetMapping("/history/cliente/{id}")
    public List<TicketHistoryDto> getHistory(@PathVariable Long id) {
        return service.getHistoryByCliente(id).stream()
                .map(TicketHistoryDto::from)
                .toList();
    }
}
