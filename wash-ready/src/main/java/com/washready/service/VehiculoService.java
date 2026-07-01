package com.washready.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.washready.model.Cliente;
import com.washready.model.Vehiculo;
import com.washready.repository.ClienteRepository;
import com.washready.repository.VehiculoRepository;

@Service
@Transactional
public class VehiculoService {
    private final VehiculoRepository repo;
    private final ClienteRepository clienteRepo;

    public VehiculoService(VehiculoRepository repo, ClienteRepository clienteRepo) {
        this.repo = repo;
        this.clienteRepo = clienteRepo;
    }

    // Listado + filtros 
    @Transactional(readOnly = true)
    public Page<Vehiculo> buscar(String color, String marca, String modelo,
                                    String matricula, String plaza, String cliente,
                                    Long clienteId, Pageable pageable) {
        String c  = norm(color), m = norm(marca), mo = norm(modelo), ma = norm(matricula), p = norm(plaza), cl = norm(cliente);
        return repo.search(c, m, mo, ma, p, cl, clienteId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Vehiculo> obtener(Long id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Vehiculo> obtenerConClientes(Long id) {
        return repo.findWithClientesById(id);
    }
    
    public Vehiculo crear(Vehiculo body) {
        validar(body);
        String mat = body.getMatricula().trim().replaceAll("\\s+", "").toUpperCase();
        if (repo.existsByMatricula(mat)) {
            throw new IllegalArgumentException("Matrícula ya existe");
        }
        body.setMatricula(mat);
        // resto de campos tal cual (marca, modelo, color, plaza)
        return repo.save(body);
    }

    // Actualizar vehículo
    public Optional<Vehiculo> actualizar(Long id, Vehiculo body) {
        validar(body);
        return repo.findById(id).map(v -> {
            if (body.getMatricula() != null) {
                String nueva = body.getMatricula().trim().replaceAll("\\s+", "").toUpperCase();
                if (!nueva.equals(v.getMatricula()) && repo.existsByMatricula(nueva)) {
                    throw new IllegalArgumentException("Matrícula ya existe");
                }
                v.setMatricula(nueva);
            }
            v.setMarca(body.getMarca());
            v.setModelo(body.getModelo());
            v.setColor(body.getColor());
            v.setPlaza(body.getPlaza());
            return repo.save(v);
        });
    }

    public boolean eliminar(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    // ===== Helpers M2M  =====
    public Optional<Vehiculo> asociarCliente(Long vehiculoId, Long clienteId) {
        var vOpt = repo.findWithClientesById(vehiculoId);
        var cOpt = clienteRepo.findById(clienteId);
        if (vOpt.isEmpty() || cOpt.isEmpty()) return Optional.empty();
        Vehiculo v = vOpt.get();
        Cliente c = cOpt.get();
        v.getClientes().add(c);
        return Optional.of(repo.save(v));
    }

    public Optional<Vehiculo> desasociarCliente(Long vehiculoId, Long clienteId) {
        var vOpt = repo.findWithClientesById(vehiculoId);
        if (vOpt.isEmpty()) return Optional.empty();
        Vehiculo v = vOpt.get();
        v.getClientes().removeIf(cl -> cl.getId().equals(clienteId));
        return Optional.of(repo.save(v));
    }

    private static String norm(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    private void validar(Vehiculo c) {
        if (c.getMatricula() == null || c.getMatricula().isBlank()) throw new IllegalArgumentException("La matrícula es obligatoria");
        if (c.getMarca() == null || c.getMarca().isBlank()) throw new IllegalArgumentException("La marca es obligatoria");
        if (c.getModelo() == null || c.getModelo().isBlank()) throw new IllegalArgumentException("El modelo es obligatorio");
    }

}
