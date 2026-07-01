package com.washready.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.washready.model.CentroTrabajo;
import com.washready.model.Servicio;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.ServicioRepository;

@Service
public class ServicioService {
    private final ServicioRepository repo;
    private final CentroTrabajoRepository centroRepo;

    public ServicioService(ServicioRepository repo, CentroTrabajoRepository centroRepo) {
        this.repo = repo;
        this.centroRepo = centroRepo;
    }

    // ===== DTO de salida para el listado =====
    public record ServicioDTO (
            Long id,
            String descripcion,
            BigDecimal importe,
            Integer stock,
            Servicio.Tipo tipo,
            boolean editable,
            boolean importeCeroEnTicket,
            boolean disponibleTodosCentros,
            List<Long> centroIds,
            List<String> centrosNombres
    ) {}

    private static ServicioDTO toDto(Servicio s) {
        List<Long> ids = s.getCentros() == null ? List.of()
                : s.getCentros().stream()
                    .map(CentroTrabajo::getId)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        List<String> nombres = s.getCentros() == null ? List.of()
                : s.getCentros().stream()
                    .map(CentroTrabajo::getNombre)
                    .filter(Objects::nonNull)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

        return new ServicioDTO(
                s.getId(),
                s.getDescripcion(),
                s.getImporte(),
                s.getStock(), // puede ser null (válido)
                s.getTipo(),
                s.isEditable(),
                s.isImporteCeroEnTicket(),
                s.isDisponibleTodosCentros(),
                ids,
                s.isDisponibleTodosCentros() ? List.of("Todos los centros") : nombres
        );
    }

    /** Listado completo ordenado por descripción (legacy en otras vistas) */
    @Transactional(readOnly = true)
    public List<Servicio> listar() {
        List<Servicio> all = repo.findAll();
        all.sort(Comparator.comparing(Servicio::getDescripcion, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    /**
     * Listado filtrado (centro opcional + búsqueda por descripción LIKE o
     * importe exacto si 'q' numérico). Devuelve DTO con centros múltiples.
     */
    @Transactional(readOnly = true)
    public List<ServicioDTO> listarPorCentroYQuery(Long centroId, String q, Boolean editable, Servicio.Tipo tipo, BigDecimal importe) {
        String qTexto = (q != null && !q.isBlank()) ? q.trim() : null;
        BigDecimal min = importe;
        BigDecimal max = importe;

        var list = repo.findFilteredWithCentros(centroId, qTexto, min, max, tipo);
        if (editable != null) {
            boolean wantsEditable = editable;
            list = list.stream()
                    .filter(item -> item.isEditable() == wantsEditable)
                    .toList();
        }
        // Opcional: orden secundario por id desc (ya viene del repo)
        return list.stream().map(ServicioService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Servicio> obtener(Long id) {
        return repo.findById(id); // @EntityGraph en repo para cargar centros
    }

    @Transactional
    public Servicio crear(Servicio s) {
        // Centos N:N desde payload (centroIds o centroId compat)
        applyCentrosFromPayload(s, s);
        // Stock puede venir null y es válido
        return repo.save(s);
    }

    @Transactional
    public Optional<Servicio> actualizar(Long id, Servicio req) {
        return repo.findById(id).map(e -> {
            e.setDescripcion(req.getDescripcion());
            e.setImporte(req.getImporte());
            e.setTipo(req.getTipo() != null ? req.getTipo() : e.getTipo());
            e.setEditable(req.isEditable());
            e.setStock(req.getStock()); // puede ser null
            e.setImporteCeroEnTicket(req.isImporteCeroEnTicket());
            e.setDisponibleTodosCentros(req.isDisponibleTodosCentros());
            // Actualizar centros si el payload trae IDs (centroIds o centroId compat)
            applyCentrosFromPayload(e, req);

            return repo.save(e);
        });
    }

    @Transactional
    public boolean eliminar(Long id) {
        return repo.findById(id).map(e -> { repo.delete(e); return true; }).orElse(false);
    }

    // ===================== Helpers =====================

    /**
     * Lee centros del payload:
     *   - centroIds (lista) NUEVO
     *   - centroId (single) COMPATIBILIDAD
     * y los aplica al target. Si no llega ninguno, NO modifica la relación.
     */
    private void applyCentrosFromPayload(Servicio target, Servicio payload) {
        if (target.isDisponibleTodosCentros() || payload.isDisponibleTodosCentros()) {
            target.getCentros().clear();
            return;
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();

        if (payload.getCentroIds() != null) {
            ids.addAll(payload.getCentroIds().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        if (payload.getCentroIdCompat() != null) {
            ids.add(payload.getCentroIdCompat());
        }

        if (ids.isEmpty()) return; // no tocar centros

        List<CentroTrabajo> centros = centroRepo.findAllById(ids);
        if (centros.size() != ids.size()) {
            Set<Long> found = centros.stream().map(CentroTrabajo::getId).collect(Collectors.toSet());
            ids.removeAll(found);
            Long faltante = ids.iterator().next();
            throw new IllegalArgumentException("Centro no encontrado: " + faltante);
        }

        centros.stream()
                .filter(c -> !c.isTransaccional())
                .findFirst()
                .ifPresent(c -> { throw new IllegalArgumentException(
                        "El centro '" + c.getNombre() + "' no puede asociarse a servicios"); });

        target.setCentros(new LinkedHashSet<>(centros));
    }

}
