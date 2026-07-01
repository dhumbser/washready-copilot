package com.washready.controller;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.washready.repository.ClienteRepository;
import com.washready.service.ClienteService;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService service;
    private final ClienteRepository clienteRepo;

    public ClienteController(ClienteService service, ClienteRepository clienteRepo) {
        this.service = service;
        this.clienteRepo = clienteRepo;
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    @GetMapping("/exists-telefono")
    public ResponseEntity<Map<String, Boolean>> existsTelefono(@RequestParam String telefono,
                                                              @RequestParam(required = false) Long excludeId) {
        String tel = (telefono == null) ? null : telefono.trim();
        if (tel == null || tel.isBlank()) {
            return ResponseEntity.ok(Map.of("exists", false));
        }
        boolean exists = clienteRepo.existsTelefonoExcludingId(tel, excludeId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @GetMapping
    public Page<Cliente> listar(@RequestParam(required = false) String q,
                               @RequestParam(required = false) Boolean noDeseado,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "15") int size) {
        var sort = Sort.by("nombre").ascending()
                .and(Sort.by("apellido").ascending())
                .and(Sort.by("id").ascending());

        return service.listar(q, noDeseado, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)), sort));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtener(@PathVariable Long id) {
        return service.obtener(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Cliente body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(body));
        } catch (DataIntegrityViolationException ex) {
            // si tu constraint se llama uk_cliente_telefono podrías afinar por mensaje, pero así ya vale
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un cliente con ese teléfono");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Cliente body) {
        try {
            return service.actualizar(id, body)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un cliente con ese teléfono");
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Solo ADMIN puede eliminar");
        return service.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

}
