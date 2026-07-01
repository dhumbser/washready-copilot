package com.washready.controller;

import com.washready.model.CentroTrabajo;
import com.washready.service.CentroTrabajoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/centros")
public class CentroTrabajoController {

    private final CentroTrabajoService service;
    private final com.washready.repository.UserDeviceRepository userDeviceRepo;

    public CentroTrabajoController(CentroTrabajoService service,
            com.washready.repository.UserDeviceRepository userDeviceRepo) {
        this.service = service;
        this.userDeviceRepo = userDeviceRepo;
    }

    public record CentroDto(
            Long id,
            Long empresaId,
            String empresaNombre,
            String nombre,
            String direccion,
            String ciudad,
            String codigoPostal,
            String correo,
            String telefono,
            Integer maxDevices,
            Boolean mostrarLogoTicket,
            long activeDevices
    ) {
        public static CentroDto from(CentroTrabajo c, long activeDevices) {
            var emp = c.getEmpresa();
            Boolean showLogo = c.getMostrarLogoTicket();
            if (showLogo == null)
                showLogo = true;
            return new CentroDto(
                    c.getId(),
                    emp != null ? emp.getId() : null,
                    emp != null ? emp.getNombre() : null,
                    c.getNombre(),
                    c.getDireccion(),
                    c.getCiudad(),
                    c.getCodigoPostal(),
                    c.getCorreo(),
                    c.getTelefono(),
                    c.getMaxDevices(),
                    showLogo,
                    activeDevices);
        }
    }

    public record CentroRequest(
            String nombre,
            String direccion,
            String ciudad,
            String codigoPostal,
            String correo,
            String telefono,
            Integer maxDevices,
            Boolean mostrarLogoTicket
    ) {
    }

    @GetMapping
    public List<CentroDto> listar(
            @RequestParam(required = false) Long empresaId,
            @RequestParam(required = false, defaultValue = "false") boolean soloTransaccional) {
        var list = soloTransaccional
                ? service.listarTransaccionales(empresaId)
                : service.listar(empresaId);
        return list.stream()
                .map(c -> CentroDto.from(c, userDeviceRepo.countActiveDevicesByCentroId(c.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CentroDto> obtener(@PathVariable Long id) {
        return service.obtener(id)
                .map(c -> CentroDto.from(c, userDeviceRepo.countActiveDevicesByCentroId(c.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestParam Long empresaId, @RequestBody CentroRequest req) {
        try {
            CentroTrabajo nuevo = new CentroTrabajo();
            nuevo.setNombre(req.nombre());
            nuevo.setDireccion(req.direccion());
            nuevo.setCiudad(req.ciudad());
            nuevo.setCodigoPostal(req.codigoPostal());
            nuevo.setCorreo(req.correo());
            nuevo.setTelefono(req.telefono());
            nuevo.setMaxDevices(req.maxDevices());
            nuevo.setMostrarLogoTicket(req.mostrarLogoTicket() == null ? Boolean.TRUE : req.mostrarLogoTicket());
            CentroTrabajo created = service.crear(empresaId, nuevo);
            return ResponseEntity.status(HttpStatus.CREATED).body(CentroDto.from(created, 0L));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
            @RequestParam(required = false) Long empresaId,
            @RequestBody CentroRequest req) {
        try {
            // Validation: Check if new limit < active devices
            Integer newLimit = req.maxDevices();
            if (newLimit != null) {
                if (newLimit < 0)
                    return ResponseEntity.badRequest().body("El límite no puede ser negativo");
                long active = userDeviceRepo.countActiveDevicesByCentroId(id);
                if (active > newLimit) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(java.util.Map.of("message",
                                    "El límite (" + newLimit + ") es menor que los dispositivos activos (" + active
                                            + "). Revoca dispositivos primero."));
                }
            }

            CentroTrabajo upd = new CentroTrabajo();
            upd.setNombre(req.nombre());
            upd.setDireccion(req.direccion());
            upd.setCiudad(req.ciudad());
            upd.setCodigoPostal(req.codigoPostal());
            upd.setCorreo(req.correo());
            upd.setTelefono(req.telefono());
            upd.setMaxDevices(req.maxDevices());
            if (req.mostrarLogoTicket() != null) {
                upd.setMostrarLogoTicket(req.mostrarLogoTicket());
            }
            return service.actualizar(id, empresaId, upd)
                    .map(c -> CentroDto.from(c, userDeviceRepo.countActiveDevicesByCentroId(c.getId())))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

}
