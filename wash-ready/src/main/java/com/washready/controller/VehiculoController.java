package com.washready.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.washready.model.Cliente;
import com.washready.model.Vehiculo;
import com.washready.repository.VehiculoRepository;
import com.washready.service.VehiculoService;

@RestController
@RequestMapping("/api/vehiculos")
public class VehiculoController {

    private final VehiculoService service;
    private final VehiculoRepository vehiculoRepo;

    public VehiculoController(VehiculoService service, VehiculoRepository vehiculoRepo) {
        this.service = service;
        this.vehiculoRepo = vehiculoRepo;
    }

    // DTO para listados
    public record VehiculoDto(Long id, String marca, String modelo, String color, String matricula, String plaza, Instant tms) {
        public static VehiculoDto from(Vehiculo v) {
            return new VehiculoDto(v.getId(), v.getMarca(), v.getModelo(), v.getColor(), v.getMatricula(), v.getPlaza(), v.getTms());
        }
    }

    // Mini DTOs
    public record VehiculoMiniDto(Long id, String matricula, String marca, String modelo, String telefono, String cliente, Long clienteId) {
        static VehiculoMiniDto of(Vehiculo v, String telefono, String cliente, Long clienteId) {
            return new VehiculoMiniDto(v.getId(), v.getMatricula(), v.getMarca(), v.getModelo(), telefono, cliente, clienteId);
        }
    }

    public record ClienteMiniDto(Long id, String nombre, String apellido, String nif, Boolean noDeseado) {
        static ClienteMiniDto from(Cliente c) {
            return new ClienteMiniDto(c.getId(), c.getNombre(), c.getApellido(), c.getNif(), c.isNoDeseado());
        }
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    // Listado con paginación y filtros
    @GetMapping
    public Page<VehiculoDto> listar(@RequestParam(required = false) String color,
                                    @RequestParam(required = false) String marca,
                                    @RequestParam(required = false) String modelo,
                                    @RequestParam(required = false) String matricula,
                                    @RequestParam(required = false) String plaza,
                                    @RequestParam(required = false, name = "cliente") String clienteNombre,
                                    @RequestParam(required = false) Long clienteId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "15") int size) {
        var sort = Sort.by("matricula").ascending().and(Sort.by("id").ascending());
        return service.buscar(color, marca, modelo, matricula, plaza, clienteNombre, clienteId,
                                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)), sort)).map(VehiculoDto::from);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehiculoDto> obtener(@PathVariable Long id) {
        return service.obtener(id)
                        .map(VehiculoDto::from)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Vehiculo body) {
        try {
            var creado = service.crear(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(VehiculoDto.from(creado));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Vehiculo body) {
        try {
            return service.actualizar(id, body)
                            .map(v -> ResponseEntity.ok(VehiculoDto.from(v)))
                            .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo ADMIN puede eliminar");
        return service.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // Autocomplete por teléfono o matrícula según el texto introducido
    @GetMapping("/search")
    public List<VehiculoMiniDto> search(@RequestParam(name = "q", required = false) String q,
                                        @RequestParam(name = "matricula", required = false) String legacyMat) {
        final String term = (q != null && !q.isBlank()) ? q.trim() : (legacyMat == null ? "" : legacyMat.trim());
        if (term.isBlank()) return List.of();

        // Se busca simultáneamente por matrícula y por teléfono: los formatos no se solapan
        // (matrícula ~7 caracteres alfanuméricos, teléfono 9 dígitos), así que no hay falsos positivos
        // y evitamos adivinar la intención del usuario ante términos ambiguos como "1234".
        final String rawDigits = term.replaceAll("\\D", "");
        final String digits = rawDigits.isBlank() ? null : rawDigits;
        final String matriculaNorm = normalizeMatricula(term);
        final String matricula = matriculaNorm.isBlank() ? null : matriculaNorm;

        var list = vehiculoRepo.searchAuto(matricula, digits);

        return list.stream().flatMap(v -> {
            final boolean vehiculoCoincide = matricula != null && containsIgnoreCase(normalizeMatricula(v.getMatricula()), matricula);

            if (v.getClientes() == null || v.getClientes().isEmpty()) {
                return vehiculoCoincide ? java.util.stream.Stream.of(VehiculoMiniDto.of(v, null, null, null))
                                        : java.util.stream.Stream.empty();
            }

            return v.getClientes().stream().filter(c -> {
                if (vehiculoCoincide) return true;
                if (digits != null && containsIgnoreCase(normalizeTelefono(c.getTelefono()), digits)) return true;
                return false;
            }).map(c -> {
                String tel = c.getTelefono();
                String cli = ((c.getNombre() == null ? "" : c.getNombre()) + " " + (c.getApellido() == null ? "" : c.getApellido())).trim();
                if (cli.isBlank()) cli = "(Sin nombre)";
                return VehiculoMiniDto.of(v, tel, cli, c.getId());
            });
        }).toList();
    }

    private static String normalizeMatricula(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\-.]", "").toLowerCase();
    }

    private static String normalizeTelefono(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static boolean containsIgnoreCase(String value, String term) {
        if (value == null || term == null || term.isBlank()) return false;
        return value.toLowerCase().contains(term.toLowerCase());
    }

    // Clientes asociados a un vehículo (M2M)
    @GetMapping("/{id}/clientes")
    public ResponseEntity<List<ClienteMiniDto>> clientes(@PathVariable Long id) {
        return service.obtenerConClientes(id)
                        .map(v -> ResponseEntity.ok(v.getClientes().stream().map(ClienteMiniDto::from).toList()))
                        .orElse(ResponseEntity.notFound().build());
    }

    // Asociar / desasociar cliente <-> vehículo
    @PostMapping("/{vehiculoId}/clientes/{clienteId}")
    public ResponseEntity<?> addCliente(@PathVariable Long vehiculoId, @PathVariable Long clienteId) {
        return service.asociarCliente(vehiculoId, clienteId)
                        .map(v -> ResponseEntity.ok().build())
                        .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{vehiculoId}/clientes/{clienteId}")
    public ResponseEntity<?> removeCliente(@PathVariable Long vehiculoId, @PathVariable Long clienteId) {
        return service.desasociarCliente(vehiculoId, clienteId)
                        .map(v -> ResponseEntity.noContent().build())
                        .orElse(ResponseEntity.notFound().build());
    }

}
