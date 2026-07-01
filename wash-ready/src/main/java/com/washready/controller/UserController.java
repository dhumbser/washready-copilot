package com.washready.controller;

import com.washready.model.CentroTrabajo;
import com.washready.model.Empresa;
import com.washready.model.User;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.EmpresaRepository;
import com.washready.repository.UserDeviceRepository;
import com.washready.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepo;
    private final EmpresaRepository empresaRepo;
    private final CentroTrabajoRepository centroRepo;
    private final UserDeviceRepository userDeviceRepo;
    private final PasswordEncoder encoder;

    public UserController(UserRepository userRepo,
            EmpresaRepository empresaRepo,
            CentroTrabajoRepository centroRepo,
            UserDeviceRepository userDeviceRepo,
            PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.empresaRepo = empresaRepo;
        this.centroRepo = centroRepo;
        this.userDeviceRepo = userDeviceRepo;
        this.encoder = encoder;
    }

    // ===== DTOs =====

    // Listado (lo que ve la tabla)
    public record UserDto(
            Long id,
            String usuario,
            String role,
            String empresaNombre,
            String centroNombre,
            LocalDateTime disabledFrom,
            boolean disabledNow,
            long activeDevices,
            long revokedDevices) {
        public static UserDto from(User u) {
            Empresa e = u.getEmpresa();
            CentroTrabajo c = u.getCentroTrabajo();
            return new UserDto(
                    u.getId(),
                    u.getUsuario(),
                    u.getRole(),
                    (e != null ? e.getNombre() : null),
                    (c != null ? c.getNombre() : null),
                    u.getDisabledFrom(),
                    false,
                    0,
                    0);
        }
    }

    // Detalle para edición
    // Incluye IDs de empresa/centro para precargar selects
    public record UserDetailDto(
            Long id,
            String usuario,
            String role,
            Long empresaId,
            String empresaNombre,
            Long centroId,
            String centroNombre,
            LocalDateTime disabledFrom) {
        public static UserDetailDto from(User u) {
            Empresa e = u.getEmpresa();
            CentroTrabajo c = u.getCentroTrabajo();
            return new UserDetailDto(
                    u.getId(),
                    u.getUsuario(),
                    u.getRole(),
                    (e != null ? e.getId() : null),
                    (e != null ? e.getNombre() : null),
                    (c != null ? c.getId() : null),
                    (c != null ? c.getNombre() : null),
                    u.getDisabledFrom());
        }
    }

    // Petición de alta/edición desde el front
    public record SaveReq(
            String usuario,
            String password,
            String role,
            Long empresaId,
            Long centroId,
            LocalDateTime disabledFrom,
            Boolean enabled,
            Boolean clearCompany) {
    }

    // DTO minimalista para /api/users/min (operarios en ticket_ficha)
    public record MiniUserDto(Long id, String usuario) {
    }

    // ===== Endpoints =====

    /** Lista completa de usuarios (para la tabla). */
    @GetMapping
    public List<UserDto> list() {
        LocalDateTime now = LocalDateTime.now();
        return userRepo.findAllWithEmpresaCentro()
                .stream()
                .map(u -> {
                    long activeDevices = userDeviceRepo.countActiveByUserId(u.getId());
                    long revokedDevices = userDeviceRepo.countRevokedByUserId(u.getId());
                    UserDto base = UserDto.from(u);
                    return new UserDto(
                            base.id(),
                            base.usuario(),
                            base.role(),
                            base.empresaNombre(),
                            base.centroNombre(),
                            base.disabledFrom(),
                            u.isDisabledAt(now),
                            activeDevices,
                            revokedDevices);
                })
                .toList();
    }

    /** Detalle de un usuario concreto (para editar). */
    @GetMapping("/{id}")
    public ResponseEntity<UserDetailDto> get(@PathVariable Long id) {
        return userRepo.findByIdWithEmpresaCentro(id)
                .map(UserDetailDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista mínima de operarios del centro actual
     * - Si es ADMIN => todos
     * - Si es normal => solo su centro
     */
    @GetMapping("/min")
    public List<MiniUserDto> min(@AuthenticationPrincipal Jwt jwt) {
        boolean admin = (jwt != null)
                && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));

        LocalDateTime now = LocalDateTime.now();

        if (admin) {
            return userRepo.findAll().stream()
                    .filter(u -> !u.isDisabledAt(now))
                    .map(u -> new MiniUserDto(u.getId(), u.getUsuario()))
                    .toList();
        }

        Long centroFromJwt = null;
        if (jwt != null) {
            Object claim = jwt.getClaim("centroId");
            if (claim != null) {
                try {
                    centroFromJwt = Long.valueOf(String.valueOf(claim));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        List<User> list = (centroFromJwt != null)
                ? userRepo.findByCentroTrabajoId(centroFromJwt)
                : List.of();

        return list.stream()
                .filter(u -> !u.isDisabledAt(now))
                .map(u -> new MiniUserDto(u.getId(), u.getUsuario()))
                .toList();
    }

    /**
     * Crear usuario.
     * - usuario y password obligatorios
     * - role por defecto ROLE_USER
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody SaveReq req) {
        if (req.usuario() == null || req.usuario().isBlank()
                || req.password() == null || req.password().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("usuario y password son obligatorios");
        }
        if (userRepo.existsByUsuario(req.usuario())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Usuario ya existe");
        }

        String effectiveRole = (req.role() == null || req.role().isBlank()) ? "ROLE_USER" : req.role().trim();
        if ("ROLE_USER".equals(effectiveRole) && req.centroId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Un usuario con rol ROLE_USER debe tener un centro asignado");
        }

        User u = new User();
        u.setUsuario(req.usuario().trim());
        u.setPassword(encoder.encode(req.password()));
        u.setRole(
                (req.role() == null || req.role().isBlank())
                        ? "ROLE_USER"
                        : req.role().trim());
        u.setDisabledFrom(req.disabledFrom());

        // Centro / Empresa
        if (req.centroId() != null) {
            CentroTrabajo c = centroRepo.findById(req.centroId())
                    .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));
            u.setCentroTrabajo(c);
            u.setEmpresa(c.getEmpresa());
        } else if (req.empresaId() != null) {
            Empresa e = empresaRepo.findById(req.empresaId())
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
            u.setEmpresa(e);
            u.setCentroTrabajo(null);
        }

        User saved = userRepo.save(u);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserDetailDto.from(saved));
    }

    /**
     * Editar usuario.
     * - usuario (username) NO se toca aquí
     * - password opcional (si viene y no está vacía => se actualiza)
     * - role opcional
     * - centro/empresa re-asociables
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SaveReq req) {
        return userRepo.findByIdWithEmpresaCentro(id).map(u -> {
            // Calcular rol resultante y si el centro quedaría vacío
            String finalRole = (req.role() != null && !req.role().isBlank()) ? req.role().trim() : u.getRole();
            boolean centroWouldBeCleared = req.centroId() == null
                    && (req.empresaId() != null || Boolean.TRUE.equals(req.clearCompany()));
            if ("ROLE_USER".equals(finalRole) && centroWouldBeCleared) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Un usuario con rol ROLE_USER debe tener un centro asignado");
            }

            LocalDateTime now = LocalDateTime.now();
            boolean wasDisabledNow = u.isDisabledAt(now);

            // Capturar centro ORIGINAL antes de cualquier modificacion
            Long originalCentroId = (u.getCentroTrabajo() != null) ? u.getCentroTrabajo().getId() : null;

            // role
            if (req.role() != null && !req.role().isBlank()) {
                u.setRole(req.role().trim());
            }

            // password
            if (req.password() != null && !req.password().isBlank()) {
                u.setPassword(encoder.encode(req.password()));
            }

            // centro / empresa
            if (req.centroId() != null) {
                // si pasa centroId => mandamos ese centro y forzamos su empresa
                CentroTrabajo c = centroRepo.findById(req.centroId())
                        .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));
                u.setCentroTrabajo(c);
                u.setEmpresa(c.getEmpresa());
            } else if (req.empresaId() != null) {
                // si NO pasa centro pero sí empresa => queda sin centro
                Empresa e = empresaRepo.findById(req.empresaId())
                        .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
                u.setEmpresa(e);
                u.setCentroTrabajo(null);
            } else if (Boolean.TRUE.equals(req.clearCompany())) {
                u.setEmpresa(null);
                u.setCentroTrabajo(null);
            }

            if (Boolean.TRUE.equals(req.enabled())) {
                u.setDisabledFrom(null);
            } else if (req.disabledFrom() != null) {
                u.setDisabledFrom(req.disabledFrom());
            }
            // ojo: si no viene ni centroId ni empresaId en la request, no tocamos las
            // asociaciones

            // Impedir deshabilitar al único admin activo
            String resultingRole = (req.role() != null && !req.role().isBlank()) ? req.role().trim() : u.getRole();
            LocalDateTime resultingDisabledFrom = Boolean.TRUE.equals(req.enabled()) ? null
                    : (req.disabledFrom() != null ? req.disabledFrom() : u.getDisabledFrom());
            if ("ROLE_ADMIN".equals(resultingRole)
                    && resultingDisabledFrom != null
                    && !resultingDisabledFrom.isAfter(now)
                    && userRepo.countActiveAdminsExcluding(id, now) == 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("No se puede deshabilitar al único administrador activo");
            }

            User upd = userRepo.save(u);

            Long newCentroId = (upd.getCentroTrabajo() != null) ? upd.getCentroTrabajo().getId() : null;
            boolean centroChanged = (originalCentroId == null && newCentroId != null) ||
                    (originalCentroId != null && !originalCentroId.equals(newCentroId));

            boolean becameDisabledNow = !wasDisabledNow && upd.isDisabledAt(now);

            if (becameDisabledNow || centroChanged) {
                userDeviceRepo.deleteByUserId(upd.getId());
            }
            return ResponseEntity.ok(UserDetailDto.from(upd));
        }).orElse(ResponseEntity.notFound().build());
    }

}
