package com.washready.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.washready.model.CentroDevice;
import com.washready.model.CentroTrabajo;
import com.washready.model.User;
import com.washready.model.UserDevice;
import com.washready.repository.CentroDeviceRepository;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.UserDeviceRepository;
import com.washready.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class DeviceStatusFilter extends OncePerRequestFilter {

    private static final String DEVICE_COOKIE = "ndid";

    private final UserDeviceRepository userDeviceRepo;
    private final UserRepository userRepo;
    private final CentroDeviceRepository centroDeviceRepo;
    private final CentroTrabajoRepository centroRepo;

    public DeviceStatusFilter(UserDeviceRepository userDeviceRepo,
            UserRepository userRepo,
            CentroDeviceRepository centroDeviceRepo,
            CentroTrabajoRepository centroRepo) {
        this.userDeviceRepo = userDeviceRepo;
        this.userRepo = userRepo;
        this.centroDeviceRepo = centroDeviceRepo;
        this.centroRepo = centroRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {

            // 1. Comprobar usuario deshabilitado (aplica a todos los roles)
            String usuario = jwtAuth.getName();
            Optional<LocalDateTime> disabledFrom = userRepo.findDisabledFromByUsuario(usuario);
            if (disabledFrom.isPresent() && disabledFrom.get() != null
                    && !disabledFrom.get().isAfter(LocalDateTime.now())) {
                // Limpieza lazy: la baja programada no pasa por update(), así que los
                // dispositivos quedarían huérfanos hasta la próxima edición manual.
                // Se eliminan aquí, en la primera petición bloqueada.
                userRepo.findByUsuario(usuario)
                        .ifPresent(u -> userDeviceRepo.deleteByUserId(u.getId()));
                response.sendError(HttpStatus.FORBIDDEN.value(), "User disabled. Session invalidated.");
                return;
            }

            // Los administradores están exentos de restricciones de dispositivo y centro
            String role = jwtAuth.getTokenAttributes().getOrDefault("role", "").toString();
            boolean isAdmin = "ROLE_ADMIN".equals(role);

            if (!isAdmin) {
                // 2. Extraer centroId del token una sola vez (se usa en device y en centro check)
                Object centroIdClaim = jwtAuth.getTokenAttributes().get("centroId");
                Long tokenCentroId = centroIdClaim instanceof Number
                        ? ((Number) centroIdClaim).longValue() : null;

                // 3. Extraer deviceId (cookie o header)
                String deviceId = extractDeviceId(request);

                if (deviceId != null && !deviceId.isBlank()) {
                    // 4. Comprobar si el dispositivo está revocado
                    Optional<LocalDateTime> revokedAt = userDeviceRepo
                            .findRevokedAtByUsuarioAndDeviceId(usuario, deviceId);
                    if (revokedAt.isPresent() && revokedAt.get() != null) {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Device revoked. Session invalidated.");
                        return;
                    }

                    // 5. Registrar o actualizar el dispositivo (false = límite alcanzado)
                    if (!touchDevice(usuario, deviceId, tokenCentroId)) {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Device limit reached. Contact your administrator.");
                        return;
                    }
                }

                // 6. Validar si el centro ha cambiado
                if (tokenCentroId != null) {
                    Long currentCentroId = userRepo.findCentroIdByUsuario(usuario).orElse(null);
                    if (currentCentroId == null || !currentCentroId.equals(tokenCentroId)) {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Center changed. Session invalidated.");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractDeviceId(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (DEVICE_COOKIE.equals(c.getName())) return c.getValue();
            }
        }
        String hdr = req.getHeader("X-Device-Id");
        return (hdr != null && !hdr.isBlank()) ? hdr : null;
    }

    private boolean touchDevice(String usuario, String deviceId, Long centroId) {
        User user = userRepo.findByUsuario(usuario).orElse(null);
        if (user == null) return true;

        LocalDateTime now = LocalDateTime.now();

        // Cargar centro una sola vez (se usa en el check de límite y en centro_device)
        CentroTrabajo centro = (centroId != null) ? centroRepo.findById(centroId).orElse(null) : null;

        // user_device: registrar en el primer acceso; actualizar last_seen_at cada 5 min
        try {
            UserDevice ud = userDeviceRepo.findByUserAndDeviceId(user, deviceId).orElse(null);
            if (ud == null) {
                // Dispositivo nuevo: verificar límite antes de registrar
                if (centro != null && centro.getMaxDevices() != null) {
                    long active = userDeviceRepo.countActiveDevicesByCentroId(centroId);
                    if (active >= centro.getMaxDevices()) {
                        return false;
                    }
                }
                UserDevice newUd = new UserDevice(user, deviceId);
                newUd.setBoundAt(now);
                newUd.setLastSeenAt(now);
                userDeviceRepo.save(newUd);
            } else if (ud.getRevokedAt() == null
                    && (ud.getLastSeenAt() == null
                        || ud.getLastSeenAt().isBefore(now.minusMinutes(5)))) {
                ud.setLastSeenAt(now);
                userDeviceRepo.save(ud);
            }
        } catch (Exception ignored) {
            // Protección ante condición de carrera en inserción simultánea
        }

        // centro_device: registrar el par (centro, dispositivo) la primera vez
        if (centro != null) {
            try {
                boolean exists = centroDeviceRepo
                        .findByCentroIdAndDeviceId(centroId, deviceId).isPresent();
                if (!exists) {
                    centroDeviceRepo.save(new CentroDevice(centro, deviceId, now));
                }
            } catch (Exception ignored) {
                // Protección ante condición de carrera
            }
        }

        return true;
    }

}
