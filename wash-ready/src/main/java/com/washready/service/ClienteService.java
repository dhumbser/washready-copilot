package com.washready.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.washready.model.Cliente;
import com.washready.repository.ClienteRepository;

@Service
@Transactional
public class ClienteService {
    
    private final ClienteRepository repo;
    public ClienteService(ClienteRepository repo) {
        this.repo = repo;
    }

    // Paginacion
    public Page<Cliente> listar(String q, Boolean noDeseado, Pageable pageable) {
        String qNorm = (q != null && !q.isBlank()) ? q.trim() : null;
        String q1 = null, q2 = null;
        if (qNorm != null) {
            String[] parts = qNorm.split("\\s+");
            if (parts.length >= 2) { q1 = parts[0]; q2 = parts[1]; }
        }
        return repo.search(qNorm, q1, q2, noDeseado, pageable);
    }

    public Optional<Cliente> obtener(Long id) {
        return repo.findById(id);
    }

    public Cliente crear(Cliente body) {
        normalize(body);
        validar(body);

        // DUPLICADO TELÉFONO
        if (body.getTelefono() != null && repo.existsByTelefono(body.getTelefono())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un cliente con ese teléfono");
        }

        return repo.save(body);
    }

    public Optional<Cliente> actualizar(Long id, Cliente body) {
        normalize(body);
        validar(body);

        // DUPLICADO TELÉFONO (excluyendo el propio id)
        if (body.getTelefono() != null && repo.existsByTelefonoAndIdNot(body.getTelefono(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un cliente con ese teléfono");
        }

        return repo.findById(id).map(c -> {
            c.setNombre(body.getNombre());
            c.setApellido(body.getApellido());
            c.setDireccion(body.getDireccion());
            c.setCodigoPostal(body.getCodigoPostal());
            c.setNif(body.getNif());
            c.setTelefono(body.getTelefono());
            c.setCorreo(body.getCorreo());
            c.setNoDeseado(body.isNoDeseado());
            return repo.save(c);
        });
    }

    public boolean eliminar(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // “simple pero efectivo”: guarda solo dígitos
        String digits = s.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private void normalize(Cliente c) {
        if (c.getNif() != null) c.setNif(c.getNif().trim().toUpperCase());
        if (c.getCorreo() != null) c.setCorreo(c.getCorreo().trim().toLowerCase());
        c.setTelefono(normalizePhone(c.getTelefono()));
    }

    private void validar(Cliente c) {
        if (c.getNombre() == null || c.getNombre().isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (c.getApellido() == null || c.getApellido().isBlank()) throw new IllegalArgumentException("El apellido es obligatorio");
        if (c.getTelefono() == null || c.getTelefono().isBlank()) throw new IllegalArgumentException("El teléfono es obligatorio");
    }

}
