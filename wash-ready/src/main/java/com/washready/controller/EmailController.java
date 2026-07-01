package com.washready.controller;

import com.washready.model.CentroTrabajo;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.service.EmailService;
import com.washready.service.ConfigCorreoService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mail")
public class EmailController {

    private final EmailService emailService;
    private final ConfigCorreoService configCorreoService;
    private final CentroTrabajoRepository centroRepo;

    public EmailController(EmailService emailService,
                           ConfigCorreoService configCorreoService,
                           CentroTrabajoRepository centroRepo) { 
        this.emailService = emailService;
        this.configCorreoService = configCorreoService;
        this.centroRepo = centroRepo; 
    }

    public record SendReq(String to, String asunto, String tipo, String body, Boolean html) {}

    // nombre del centro desde el JWT o sino BD
    private String resolveCentroNombre(Jwt jwt) {
        if (jwt == null) return "Centro";
        // 1) Intentar directamente del claim "centro"
        String c = jwt.getClaimAsString("centro");
        if (c != null && !c.isBlank()) return c;

        // Fallback buscar por centroId en BD
        Object cid = jwt.getClaim("centroId");
        Long id = null;
        if (cid instanceof Number n) id = n.longValue();
        else if (cid != null) try { id = Long.valueOf(cid.toString()); } catch (Exception ignored) {}
        if (id != null) {
            return centroRepo.findById(id)
                    .map(CentroTrabajo::getNombre)
                    .orElse("Centro " + id);
        }
        return "Centro";
    }

    // map del selector 
    private String mapTipoAsunto(String tipo) {
        if (tipo == null) return "Otros";
        return switch (tipo.trim().toLowerCase()) {
            case "pedido productos", "pedido", "productos" -> "Pedido de productos";
            case "caja" -> "Caja";
            case "incidencias", "incidencia" -> "Incidencias";
            default -> "Otros";
        };
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@AuthenticationPrincipal Jwt jwt, @RequestBody SendReq req) {
        // destinatario
        String to = (req.to() != null && !req.to().isBlank())
                ? req.to().trim()
                : configCorreoService.getDefaultTo().orElse(null);
        if (to == null || to.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("No hay destinatario: indique 'to' o configure un correo predeterminado.");
        }

        // Armar asunto 
        String base = (req.asunto() != null && !req.asunto().isBlank())
                ? req.asunto().trim()
                : mapTipoAsunto(req.tipo());

        // Prefijar SIEMPRE el nombre del centro del usuario
        String centroNombre = resolveCentroNombre(jwt);
        String finalSubject = "[" + centroNombre + "] " + base;

        boolean html = Boolean.TRUE.equals(req.html());
        emailService.send(new EmailService.Email(to, finalSubject, req.body() != null ? req.body() : "", html));
        return ResponseEntity.ok().build();
    }
}
