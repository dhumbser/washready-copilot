package com.washready.controller;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.washready.model.Servicio;
import com.washready.service.ServicioService;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    private final ServicioService service;

    public ServicioController(ServicioService service) {
        this.service = service;
    }

    // ===== Helpers de auth/claims =====
    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }
    private Long claimLong(Jwt jwt, String name) {
        if (jwt == null) return null;
        Object v = jwt.getClaim(name);
        if (v instanceof Number n) return n.longValue();
        try { return v != null ? Long.valueOf(v.toString()) : null; } catch (Exception e) { return null; }
    }

    // ====== DTOs request/response ======
    public record UpsertReq(
            String descripcion,
            BigDecimal importe,
            Integer stock,
            Servicio.Tipo tipo,
            Boolean editable,
            Boolean importeCeroEnTicket,
            Boolean disponibleTodosCentros,
            @JsonAlias({"centroIds","centrosIds"}) List<Long> centroIds // acepta ambos nombres
    ) {}

    public record ServicioDto(
            Long id,
            String descripcion,
            BigDecimal importe,
            Integer stock,
            String tipo,
            boolean editable,
            boolean importeCeroEnTicket,
            boolean disponibleTodosCentros,
            List<Map<String, Object>> centros // [{id, nombre}, ...]
    ) {
        static ServicioDto fromEntity(Servicio s) {
            List<Map<String, Object>> cs = (s.getCentros() == null) ? List.of()
                    : s.getCentros().stream()
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", c.getId());
                        m.put("nombre", c.getNombre());
                        return m;
                    })
                    .collect(Collectors.toList());

            if (s.isDisponibleTodosCentros() && cs.isEmpty()) {
                Map<String, Object> all = new LinkedHashMap<>();
                all.put("id", null);
                all.put("nombre", "Todos los centros");
                cs = List.of(all);
            }

            return new ServicioDto(
                    s.getId(),
                    s.getDescripcion(),
                    s.getImporte(),
                    s.getStock(),
                    s.getTipo() != null ? s.getTipo().name() : null,
                    s.isEditable(),
                    s.isImporteCeroEnTicket(),
                    s.isDisponibleTodosCentros(),
                    cs
            );
        }
    }

    // ====== LISTAR ======
    // ADMIN: puede filtrar por centroId; USER: forzado a su centro.
    @GetMapping
    public List<ServicioService.ServicioDTO> listar(@RequestParam(required = false) Long centroId,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Boolean editable,
                                                    @RequestParam(required = false) BigDecimal importe,
                                                    @RequestParam(required = false) String tipo,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Long effectiveCentro = isAdmin(jwt) ? centroId : claimLong(jwt, "centroId");
        Servicio.Tipo tipoEnum = null;
        if (tipo != null && !tipo.isBlank()) {
            try { tipoEnum = Servicio.Tipo.valueOf(tipo.trim().toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        return service.listarPorCentroYQuery(effectiveCentro, q, editable, tipoEnum, importe);
    }

    // ====== OBTENER ======
    @GetMapping("/{id}")
    public ResponseEntity<ServicioDto> get(@PathVariable Long id) {
        return service.obtener(id)
                        .map(ServicioDto::fromEntity)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }

    // ====== CREAR (ADMIN) ======
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody UpsertReq body, @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo ADMIN puede crear");

        try {
            Servicio s = new Servicio();
            s.setDescripcion(Optional.ofNullable(body.descripcion()).orElse("").trim());
            s.setImporte(body.importe() != null ? body.importe() : BigDecimal.ZERO);
            s.setStock(body.stock()); // puede ser null
            if (body.tipo() == null) {
                throw new IllegalArgumentException("Tipo requerido");
            }
            s.setTipo(body.tipo());
            s.setEditable(Boolean.TRUE.equals(body.editable()));
            s.setImporteCeroEnTicket(Boolean.TRUE.equals(body.importeCeroEnTicket()));
            s.setDisponibleTodosCentros(Boolean.TRUE.equals(body.disponibleTodosCentros()));

            // Centros (N:N): se pasan en transitorio para que el Service los resuelva
            if (body.centroIds() != null) {
                s.setCentroIds(body.centroIds());
            }

            Servicio creado = service.crear(s);
            return ResponseEntity.status(HttpStatus.CREATED).body(ServicioDto.fromEntity(creado));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    // ====== ACTUALIZAR ======
    // ADMIN: puede cambiar todo (incl. centros/ editable)
    // USER : solo si editable=true y el servicio pertenece a su centro; NO puede cambiar centros ni editable
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id,
                                        @RequestBody UpsertReq body,
                                        @AuthenticationPrincipal Jwt jwt) {
        boolean admin = isAdmin(jwt);

        return service.obtener(id).map(existing -> {
            if (!admin) {
                // Reglas USER
                if (!existing.isEditable()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No editable para ROLE_USER");
                }
                Long userCentro = claimLong(jwt, "centroId");
                boolean belongs = existing.getCentros() != null &&
                        existing.getCentros().stream().anyMatch(c -> Objects.equals(c.getId(), userCentro));
                if (!belongs) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No puedes editar servicios de otro centro");
                }
            }

            // Construimos un payload con SOLO los campos permitidos
            Servicio payload = new Servicio();
            payload.setDescripcion(Optional.ofNullable(body.descripcion()).orElse("").trim());
            payload.setImporte(body.importe() != null ? body.importe() : existing.getImporte());
            payload.setStock(body.stock()); // puede ser null
            payload.setTipo(body.tipo() != null ? body.tipo() : existing.getTipo());
            payload.setImporteCeroEnTicket(body.importeCeroEnTicket() != null ? body.importeCeroEnTicket() : existing.isImporteCeroEnTicket());
            payload.setDisponibleTodosCentros(body.disponibleTodosCentros() != null ? body.disponibleTodosCentros() : existing.isDisponibleTodosCentros());

            if (admin) {
                payload.setEditable(body.editable() != null ? body.editable() : existing.isEditable());
                if (body.centroIds() != null) {
                    payload.setCentroIds(body.centroIds()); // Service actualizará la N:N
                }
            } else {
                // USER: NO tocar editable ni centros -> no seteamos esos campos en payload
                payload.setEditable(existing.isEditable());
            }

            try {
                return service.actualizar(id, payload)
                        .map(ServicioController.ServicioDto::fromEntity)
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // ====== ELIMINAR (ADMIN) ======
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @AuthenticationPrincipal Jwt jwt){
        if (!isAdmin(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo ADMIN puede eliminar");
        return service.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

}
