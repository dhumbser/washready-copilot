package com.washready.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.washready.model.User;
import com.washready.model.UserDevice;
import com.washready.repository.UserDeviceRepository;
import com.washready.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/devices")
public class UserDeviceController {

    private final UserRepository userRepo;
    private final UserDeviceRepository userDeviceRepo;

    public UserDeviceController(UserRepository userRepo,
            UserDeviceRepository userDeviceRepo) {
        this.userRepo = userRepo;
        this.userDeviceRepo = userDeviceRepo;
    }

    public record UserDeviceDto(
            String deviceId,
            LocalDateTime boundAt,
            LocalDateTime lastSeenAt,
            LocalDateTime revokedAt) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }

        List<UserDevice> devices = userDeviceRepo.findByUserIdOrderByLastSeenAtDesc(userId);
        List<UserDeviceDto> payload = devices.stream()
                .map(device -> {
                    return new UserDeviceDto(
                            device.getDeviceId(),
                            device.getBoundAt(),
                            device.getLastSeenAt(),
                            device.getRevokedAt());
                })
                .sorted(Comparator.comparing(UserDeviceDto::lastSeenAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return ResponseEntity.ok(payload);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> delete(@PathVariable Long userId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Jwt jwt) {
        ResponseEntity<?> adminGuard = requireAdmin(jwt);
        if (adminGuard != null)
            return adminGuard;

        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }

        // Buscamos el dispositivo
        UserDevice device = userDeviceRepo.findByUserIdAndDeviceId(userId, deviceId);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado");
        }

        // Opcional: Validar que esté revocado si queremos ser estrictos,
        // pero el usuario pidió "eliminar definitivamente" para limpiar.
        // Permitiremos borrar cualquiera si es admin.
        userDeviceRepo.delete(device);

        return ResponseEntity.ok().build();
    }

    private ResponseEntity<?> requireAdmin(Jwt jwt) {
        if (jwt == null || !"ROLE_ADMIN".equals(jwt.getClaimAsString("role"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso restringido a administradores");
        }
        return null;
    }

}
