package com.washready.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.washready.model.CentroDevice;
import com.washready.model.CentroTrabajo;
import com.washready.model.UserDevice;
import com.washready.repository.CentroDeviceRepository;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.UserDeviceRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/centros/{centroId}/devices")
public class CentroDeviceController {

    private final CentroTrabajoRepository centroRepo;
    private final UserDeviceRepository userDeviceRepo;
    private final CentroDeviceRepository centroDeviceRepo;

    public CentroDeviceController(CentroTrabajoRepository centroRepo,
            UserDeviceRepository userDeviceRepo,
            CentroDeviceRepository centroDeviceRepo) {
        this.centroRepo = centroRepo;
        this.userDeviceRepo = userDeviceRepo;
        this.centroDeviceRepo = centroDeviceRepo;
    }

    public record DeviceActiveDto(
            String deviceId,
            String alias,
            LocalDateTime lastSeenAt,
            LocalDateTime boundAt,
            List<String> usuarios) {
    }

    public record DeviceManagementDto(
            String deviceId,
            String alias,
            LocalDateTime lastSeenAt,
            LocalDateTime boundAt,
            List<String> usuarios,
            boolean revoked) {
    }

    public record DeviceLimitRequest(Integer maxDevices) {
    }

    @GetMapping
    public ResponseEntity<?> listAll(@PathVariable Long centroId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null) return adminGuard;

        Map<String, String> aliasMap = centroDeviceRepo
                .findByCentroTrabajoIdOrderByRegisteredAtDesc(centroId).stream()
                .filter(cd -> cd.getAlias() != null)
                .collect(Collectors.toMap(CentroDevice::getDeviceId, CentroDevice::getAlias,
                        (a, b) -> a));

        Map<String, List<UserDevice>> byDevice = userDeviceRepo.findAllByCentroId(centroId)
                .stream().collect(Collectors.groupingBy(UserDevice::getDeviceId));

        List<DeviceManagementDto> result = byDevice.entrySet().stream().map(entry -> {
            String deviceId = entry.getKey();
            List<UserDevice> rows = entry.getValue();
            boolean revoked = rows.stream().allMatch(ud -> ud.getRevokedAt() != null);
            LocalDateTime lastSeen = rows.stream().map(UserDevice::getLastSeenAt)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            LocalDateTime firstBound = rows.stream().map(UserDevice::getBoundAt)
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            List<String> usuarios = rows.stream()
                    .map(ud -> ud.getUser().getUsuario()).distinct().toList();
            return new DeviceManagementDto(deviceId, aliasMap.get(deviceId),
                    lastSeen, firstBound, usuarios, revoked);
        }).sorted(Comparator.comparing(DeviceManagementDto::revoked)
                .thenComparing(DeviceManagementDto::lastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/active")
    public ResponseEntity<?> listActive(@PathVariable Long centroId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        List<DeviceActiveDto> result = listActiveDevices(centroId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{deviceId}/revoke")
    @Transactional
    public ResponseEntity<?> revoke(@PathVariable Long centroId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        LocalDateTime now = LocalDateTime.now();
        int revoked = userDeviceRepo.revokeByCentroAndDeviceId(centroId, deviceId, now);
        if (revoked == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("revokedUserDevices", revoked);
        return ResponseEntity.ok(payload);
    }

    @PutMapping("/{deviceId}/reactivate")
    @Transactional
    public ResponseEntity<?> reactivate(@PathVariable Long centroId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        LocalDateTime now = LocalDateTime.now();

        // Check Limit
        CentroTrabajo centro = centroRepo.findById(centroId).orElse(null);
        if (centro != null && centro.getMaxDevices() != null) {
            long currentActive = userDeviceRepo.countActiveDevicesByCentroId(centroId);
            // Si el dispositivo a reactivar YA está activo (por algún error de estado), no
            // suma
            boolean alreadyActive = userDeviceRepo.countActiveByCentroIdAndDeviceId(centroId, deviceId) > 0;

            if (!alreadyActive && currentActive >= centro.getMaxDevices()) {
                Map<String, Object> error = new HashMap<>();
                error.put("message", "No se puede reactivar: Límite de dispositivos alcanzado.");
                error.put("limit", centro.getMaxDevices());
                error.put("activeCount", currentActive);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }
        }

        // Reactivamos TODOS los usuarios de este centro que tengan este deviceId
        int reactivated = userDeviceRepo.reactivateByCentroAndDeviceId(centroId, deviceId, now);

        if (reactivated == 0) {
            // Podría ser que no exista o que ya esté activo.
            // Verificamos si existe alguno "activo" para dar mejor feedback?
            // De momento simple: si 0, quizás es que no hay nada que reactivar.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontraron dispositivos revocados para reactivar.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("reactivatedUserDevices", reactivated);
        payload.put("reactivatedUserDevices", reactivated);
        return ResponseEntity.ok(payload);
    }

    public record DeviceAliasRequest(String alias) {
    }

    @PutMapping("/{deviceId}/alias")
    @Transactional
    public ResponseEntity<?> updateAlias(@PathVariable Long centroId,
            @PathVariable String deviceId,
            @RequestBody DeviceAliasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        String newAlias = req.alias() != null ? req.alias().trim() : null;
        if (newAlias != null && newAlias.isBlank())
            newAlias = null;

        CentroDevice cd = centroDeviceRepo.findByCentroIdAndDeviceId(centroId, deviceId).orElse(null);
        if (cd == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado");
        }
        cd.setAlias(newAlias);
        centroDeviceRepo.save(cd);
        return ResponseEntity.ok(Map.of("deviceId", deviceId, "alias", newAlias == null ? "" : newAlias));
    }

    @DeleteMapping("/{deviceId}")
    @Transactional
    public ResponseEntity<?> deleteRevoked(@PathVariable Long centroId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null) return adminGuard;

        long activeCount = userDeviceRepo.countAnyActiveByCentroIdAndDeviceId(centroId, deviceId);
        if (activeCount > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("No se puede eliminar: el dispositivo aún tiene sesiones activas.");
        }

        int deleted = userDeviceRepo.deleteByCentroIdAndDeviceId(centroId, deviceId);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado");
        }

        centroDeviceRepo.findByCentroIdAndDeviceId(centroId, deviceId)
                .ifPresent(centroDeviceRepo::delete);

        return ResponseEntity.ok(Map.of("deviceId", deviceId, "deleted", deleted));
    }

    @PatchMapping("/limit")
    public ResponseEntity<?> updateLimit(@PathVariable Long centroId,
            @RequestBody DeviceLimitRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        CentroTrabajo centro = centroRepo.findById(centroId)
                .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado"));

        Integer newLimit = req == null ? null : req.maxDevices();
        if (newLimit != null && newLimit < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El límite debe ser >= 0");
        }

        long activeCount = userDeviceRepo.countActiveDevicesByCentroId(centroId);
        if (newLimit != null && activeCount > newLimit) {
            List<DeviceActiveDto> activeDevices = listActiveDevices(centroId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", "El límite es menor que los dispositivos activos. Revoca dispositivos específicos.");
            payload.put("activeCount", activeCount);
            payload.put("limit", newLimit);
            payload.put("devices", activeDevices);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
        }

        centro.setMaxDevices(newLimit);
        centroRepo.save(centro);

        Map<String, Object> payload = new HashMap<>();
        payload.put("limit", newLimit);
        payload.put("activeCount", activeCount);
        return ResponseEntity.ok(payload);
    }

    private List<DeviceActiveDto> listActiveDevices(Long centroId) {
        // Alias canónico por dispositivo, leído desde centro_device
        Map<String, String> aliasMap = centroDeviceRepo
                .findByCentroTrabajoIdOrderByRegisteredAtDesc(centroId)
                .stream()
                .filter(cd -> cd.getAlias() != null)
                .collect(Collectors.toMap(CentroDevice::getDeviceId, CentroDevice::getAlias,
                        (a, b) -> a));

        List<UserDevice> devices = userDeviceRepo.findActiveByCentroId(centroId);
        Map<String, DeviceActiveDto> aggregated = devices.stream()
                .collect(Collectors.toMap(
                        UserDevice::getDeviceId,
                        device -> new DeviceActiveDto(
                                device.getDeviceId(),
                                aliasMap.get(device.getDeviceId()),
                                device.getLastSeenAt(),
                                device.getBoundAt(),
                                new ArrayList<>(List.of(device.getUser().getUsuario()))),
                        (existing, incoming) -> {
                            existing.usuarios().addAll(incoming.usuarios());
                            if (incoming.lastSeenAt() != null
                                    && (existing.lastSeenAt() == null
                                            || incoming.lastSeenAt().isAfter(existing.lastSeenAt()))) {
                                return new DeviceActiveDto(
                                        existing.deviceId(),
                                        existing.alias(),
                                        incoming.lastSeenAt(),
                                        existing.boundAt(),
                                        existing.usuarios());
                            }
                            return existing;
                        }));

        return aggregated.values().stream()
                .sorted(Comparator.comparing(DeviceActiveDto::lastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ResponseEntity<?> requireAdmin(Jwt jwt) {
        if (jwt == null || !"ROLE_ADMIN".equals(jwt.getClaimAsString("role"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso restringido a administradores");
        }
        return null;
    }

}
