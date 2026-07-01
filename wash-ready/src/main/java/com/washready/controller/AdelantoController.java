package com.washready.controller;

import com.washready.model.Adelanto;
import com.washready.model.User;
import com.washready.repository.UserRepository;
import com.washready.service.AdelantoService;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/adelantos")
public class AdelantoController {

    private static final Logger log = LoggerFactory.getLogger(AdelantoController.class);

    private final AdelantoService service;
    private final UserRepository userRepo;

    public AdelantoController(AdelantoService service, UserRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    // ===== DTOs =====
    public record CrearReq(BigDecimal importe, String operarioNombre, String operarioApellido, String operarioNif) {
    }

    public record EstadoReq(String estado, String motivo) {
    }

    public record AdelantoDto(
            Long id,
            BigDecimal importe,
            LocalDateTime creadoAt,
            String estado,
            Long userId, String userNombre,
            Long centroId, String centroNombre,
            LocalDateTime decididoAt,
            String operarioNombre, String operarioApellido, String operarioNif,
            String motivoRechazo) {
        static AdelantoDto from(Adelanto a) {
            User u = a.getUser();
            var c = a.getCentro();
            return new AdelantoDto(
                    a.getId(),
                    a.getImporte(),
                    a.getCreadoAt(),
                    a.getEstado().name(),
                    u != null ? u.getId() : null,
                    u != null ? u.getUsuario() : null,
                    c != null ? c.getId() : null,
                    c != null ? c.getNombre() : null,
                    a.getDecididoAt(),
                    a.getOperarioNombre(),
                    a.getOperarioApellido(),
                    a.getOperarioNif(),
                    a.getMotivoRechazo());
        }
    }

    public record PageDto<T>(List<T> content, long totalElements, int totalPages, int number, int size) {
        static <R> PageDto<R> from(Page<R> p) {
            return new PageDto<>(p.getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
        }
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    private Long userIdFromJwt(Jwt jwt) {
        if (jwt == null)
            return null;
        String username = jwt.getSubject();
        if (username == null || username.isBlank())
            return null;
        return userRepo.findByUsuario(username).map(User::getId).orElse(null);
    }

    // Helpers parse
    private Adelanto.Estado parseEstado(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return Adelanto.Estado.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseFrom(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseToExclusive(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return LocalDate.parse(s).plusDays(1).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Empleado: crear
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody CrearReq body, @AuthenticationPrincipal Jwt jwt) {
        try {
            Long uid = userIdFromJwt(jwt);
            if (uid == null)
                return ResponseEntity.status(401).build();
            var a = service.crear(uid, body.importe(), body.operarioNombre(), body.operarioApellido(),
                    body.operarioNif());
            return ResponseEntity.ok(AdelantoDto.from(a));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (AdelantoService.NotificationException ex) {
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    // ===== Empleado: disponible este mes
    @GetMapping("/my/disponible")
    public ResponseEntity<?> disponible(@AuthenticationPrincipal Jwt jwt) {
        Long uid = userIdFromJwt(jwt);
        if (uid == null) return ResponseEntity.status(401).build();
        var disponible = service.disponibleMes(uid);
        return ResponseEntity.ok(Map.of("disponible", disponible, "limite", new java.math.BigDecimal("300.00")));
    }

    // ===== Listado paginado del usuario (con filtros)
    @GetMapping("/my")
    public ResponseEntity<PageDto<AdelantoDto>> misSolicitudes(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false, name = "from") String fromDate, // yyyy-MM-dd
            @RequestParam(required = false, name = "to") String toDate, // yyyy-MM-dd
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {

        Long uid = userIdFromJwt(jwt);
        if (uid == null)
            return ResponseEntity.status(401).build();

        var p = service.buscar(uid, parseEstado(estado), parseFrom(fromDate), parseToExclusive(toDate), page, size)
                .map(AdelantoDto::from);
        return ResponseEntity.ok(PageDto.from(p));
    }

    // ===== Admin: listado global (con filtros y paginación)
    @GetMapping("/admin")
    public ResponseEntity<?> adminListado(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Solo ADMIN");
        var p = service.buscar(null, parseEstado(estado), parseFrom(fromDate), parseToExclusive(toDate), page, size)
                .map(AdelantoDto::from);
        return ResponseEntity.ok(PageDto.from(p));
    }

    // ===== Admin: decidir
    @PutMapping("/{id}/estado")
    public ResponseEntity<?> decidir(@PathVariable Long id, @RequestBody EstadoReq body,
            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt))
            return ResponseEntity.status(403).body("Solo ADMIN");
        try {
            Long adminId = userIdFromJwt(jwt);
            var estado = parseEstado(body.estado());
            var a = service.decidir(id, adminId, estado, body.motivo());
            return ResponseEntity.ok(AdelantoDto.from(a));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    // ===== Empleado: cancelar solicitud propia pendiente
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long uid = userIdFromJwt(jwt);
        if (uid == null)
            return ResponseEntity.status(401).build();
        try {
            var a = service.cancelar(id, uid);
            return ResponseEntity.ok(AdelantoDto.from(a));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(ex.getMessage());
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping(value = "/{id}/print")
    public ResponseEntity<?> print(@PathVariable Long id, Locale locale) {
        try {
            byte[] pdf = service.printPdf(id, locale);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline().filename("adelanto-" + id + ".pdf").build());
            headers.setCacheControl(CacheControl.noStore());

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (IllegalStateException ex) {
            log.error("Error generando PDF para adelanto {}", id, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No se pudo generar el PDF");
        }
    }

    // ===== Confirmación por enlace (correo)
    @GetMapping(value = "/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmarPage(@RequestParam String token) {
        com.washready.model.Adelanto.Estado estado = service.getEstado(token);

        if (estado == null) {
            return ResponseEntity.ok("""
                <!doctype html><html lang="es"><meta charset="utf-8"><title>Error</title>
                <style>*{box-sizing:border-box}body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}.wrap{max-width:460px;margin:0 auto}.box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}</style>
                <body><div class="wrap"><h2>Enlace no válido</h2><div class="box"><p>Este enlace no es válido o ya no existe.</p></div></div></body></html>
                """);
        }

        if (estado != com.washready.model.Adelanto.Estado.PENDIENTE) {
            String msgName = switch (estado) {
                case ACEPTADO -> "Aceptada";
                case RECHAZADO -> "Rechazada";
                case CANCELADO -> "Cancelada";
                default -> estado.name();
            };
            String htmlR = """
                <!doctype html><html lang="es"><meta charset="utf-8">
                <title>Solicitud ya procesada</title>
                <style>*{box-sizing:border-box}body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}.wrap{max-width:460px;margin:0 auto}h2{margin:0 0 16px;font-size:1.35rem;font-weight:700}.box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}.box p{margin:0}.tag{display:inline-block;margin-top:12px;font-size:.8rem;font-weight:600;padding:2px 10px;border-radius:999px;background:#f3f4f6;border:1px solid #e5e7eb;color:#374151}</style>
                <body><div class="wrap">
                  <h2>Solicitud ya procesada</h2>
                  <div class="box">
                    <p>Esta solicitud ya fue procesada y no puede modificarse.</p>
                    <span class="tag">%s</span>
                  </div>
                </div></body></html>
                """.formatted(msgName);
            return ResponseEntity.ok(htmlR);
        }

        String safeToken = HtmlUtils.htmlEscape(token);
        String html = """
            <!doctype html><html lang="es"><meta charset="utf-8">
            <title>Decidir adelanto</title>
            <style>
              *{box-sizing:border-box}
              body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}
              .wrap{max-width:460px;margin:0 auto}
              h2{margin:0 0 16px;font-size:1.35rem;font-weight:700}
              .box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}
              .box p{margin:0}
              .actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:18px}
              .btn{padding:10px 22px;border-radius:8px;font-size:.95rem;font-weight:600;cursor:pointer;border:1.5px solid transparent;transition:opacity .15s,transform .1s}
              .btn:hover{opacity:.85}
              .btn:active{transform:scale(.97)}
              .btn-ok{background:#166534;color:#fff;border-color:#166534}
              .btn-no{background:#fff;color:#991b1b;border-color:#fca5a5}
            </style>
            <body><div class="wrap">
              <h2>Solicitud de adelanto</h2>
              <div class="box">
                <p>Revisa la solicitud y acepta o rechaza el adelanto.</p>
                <form method="POST" action="/api/adelantos/confirm">
                  <input type="hidden" name="token" value="%s">
                  <div class="actions">
                    <button class="btn btn-no" type="submit" name="accion" value="RECHAZAR">Rechazar</button>
                    <button class="btn btn-ok" type="submit" name="accion" value="ACEPTAR">Aceptar adelanto</button>
                  </div>
                </form>
              </div>
            </div></body></html>
            """.formatted(safeToken);
        return ResponseEntity.ok(html);
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> confirmarDo(@RequestParam String token,
            @RequestParam(defaultValue = "ACEPTAR") String accion) {
        String msg;
        try {
            boolean aceptar = !"RECHAZAR".equalsIgnoreCase(accion);
            msg = service.decidirPorToken(token, aceptar);
        } catch (IllegalArgumentException ex) {
            msg = ex.getMessage();
        }

        String html = """
            <!doctype html><html lang="es"><meta charset="utf-8">
            <title>Solicitud de adelanto</title>
            <style>
              *{box-sizing:border-box}
              body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}
              .wrap{max-width:460px;margin:0 auto}
              h2{margin:0 0 16px;font-size:1.35rem;font-weight:700}
              .box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}
              .box p{margin:0 0 16px}
              a.btn-back{display:inline-block;padding:9px 20px;border-radius:8px;font-size:.9rem;font-weight:600;text-decoration:none;background:#fff;color:#374151;border:1.5px solid #d1d5db}
              a.btn-back:hover{background:#f3f4f6}
            </style>
            <body><div class="wrap">
              <h2>Solicitud de adelanto</h2>
              <div class="box">
                <p>%s</p>
                <a class="btn-back" href="/">Volver a Wash &amp; Ready</a>
              </div>
            </div></body></html>
            """.formatted(HtmlUtils.htmlEscape(msg == null ? "" : msg));
        return ResponseEntity.ok(html);
    }

}
