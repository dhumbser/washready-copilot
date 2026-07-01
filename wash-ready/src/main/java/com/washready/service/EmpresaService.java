package com.washready.service;

import com.washready.model.Empresa;
import com.washready.repository.EmpresaRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class EmpresaService {
    private final EmpresaRepository repo;
    public EmpresaService(EmpresaRepository repo) { this.repo = repo; }

    public List<Empresa> listar() {
        List<Empresa> all = repo.findAll();
        all.sort(Comparator.comparing(Empresa::getNombre, String.CASE_INSENSITIVE_ORDER));
        return all;
    }
    
    public Optional<Empresa> obtener(Long id) { return repo.findById(id); }

    public Empresa crear(Empresa e) {
        if (repo.existsByCif(e.getCif())) throw new IllegalArgumentException("CIF ya existe");
        return repo.save(e);
    }

    public Optional<Empresa> actualizar(Long id, Empresa req) {
        return repo.findById(id).map(e -> {
            if (!e.getCif().equals(req.getCif()) && repo.existsByCif(req.getCif())) {
                throw new IllegalArgumentException("CIF ya existe");
            }
            e.setNombre(req.getNombre());
            e.setDireccion(req.getDireccion());
            e.setMunicipio(req.getMunicipio());
            e.setCodigoPostal(req.getCodigoPostal());
            e.setProvincia(req.getProvincia());
            e.setPais(req.getPais());
            e.setCorreo(req.getCorreo());
            e.setTelefono(req.getTelefono());
            e.setCif(req.getCif());
            return repo.save(e);
        });
    }

    public boolean eliminar(Long id) {
        return repo.findById(id).map(e -> { repo.delete(e); return true; }).orElse(false);
    }
}
