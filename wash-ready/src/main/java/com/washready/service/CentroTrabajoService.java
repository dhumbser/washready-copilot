package com.washready.service;

import com.washready.model.CentroTrabajo;
import com.washready.model.Empresa;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.EmpresaRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CentroTrabajoService {
    private final CentroTrabajoRepository repo;
    private final EmpresaRepository empresaRepo;

    public CentroTrabajoService(CentroTrabajoRepository repo, EmpresaRepository empresaRepo) {
        this.repo = repo;
        this.empresaRepo = empresaRepo;
    }

    public List<CentroTrabajo> listar(Long empresaId) {
        List<CentroTrabajo> all = (empresaId == null) ? repo.findAll() : repo.findByEmpresaId(empresaId);
        all.sort(Comparator.comparing(CentroTrabajo::getNombre, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public List<CentroTrabajo> listarTransaccionales(Long empresaId) {
        List<CentroTrabajo> all = (empresaId == null)
                ? repo.findByTransaccionalTrue()
                : repo.findByEmpresaIdAndTransaccionalTrue(empresaId);
        all.sort(Comparator.comparing(CentroTrabajo::getNombre, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public Optional<CentroTrabajo> obtener(Long id) { return repo.findById(id); }

    public CentroTrabajo crear(Long empresaId, CentroTrabajo ct) {
        Empresa emp = empresaRepo.findById(empresaId).orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        ct.setEmpresa(emp);
        return repo.save(ct);
    }

    public Optional<CentroTrabajo> actualizar(Long id, Long empresaId, CentroTrabajo req) {
        return repo.findById(id).map(e -> {
            if (empresaId != null) {
                Empresa emp = empresaRepo.findById(empresaId).orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
                e.setEmpresa(emp);
            }
            e.setNombre(req.getNombre());
            e.setDireccion(req.getDireccion());
            e.setCiudad(req.getCiudad());
            e.setCodigoPostal(req.getCodigoPostal());
            e.setCorreo(req.getCorreo());
            e.setTelefono(req.getTelefono());
            e.setMaxDevices(req.getMaxDevices());
            if (req.getMostrarLogoTicket() != null) {
                e.setMostrarLogoTicket(req.getMostrarLogoTicket());
            }
            return repo.save(e);
        });
    }

}
