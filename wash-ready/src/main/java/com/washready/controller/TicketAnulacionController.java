package com.washready.controller;

import com.washready.model.User;
import com.washready.repository.UserRepository;
import com.washready.service.TicketAnulacionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/tickets")
public class TicketAnulacionController {

  private final TicketAnulacionService svc;
  private final UserRepository userRepo;

  public TicketAnulacionController(TicketAnulacionService svc, UserRepository userRepo){
    this.svc = svc;
    this.userRepo = userRepo;
  }

  public record SolicitudDTO(String motivo) {}

  private Long resolveUserId(Jwt jwt) {
    if (jwt == null) return null;
    Object uid = jwt.getClaim("userId");
    if (uid == null) {
      uid = jwt.getClaim("uid");
    }
    if (uid instanceof Number n) {
      return n.longValue();
    }
    String username = jwt.getSubject();
    if (username == null || username.isBlank()) return null;
    return userRepo.findByUsuario(username).map(User::getId).orElse(null);
  }

  @PostMapping("/{id}/anular")
  public ResponseEntity<?> anularDirecto(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    if (jwt == null || !"ROLE_ADMIN".equals(jwt.getClaimAsString("role")))
      return ResponseEntity.status(403).build();
    try {
      svc.anularDirecto(id);
      return ResponseEntity.ok().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/anulacion/solicitar")
  public ResponseEntity<?> solicitar(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable Long id,
                                     @RequestBody(required=false) SolicitudDTO dto){
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = resolveUserId(jwt);
    Long centroId = (jwt.getClaim("centroId") instanceof Number n) ? n.longValue() : null;
    String usuarioNom = jwt.getClaimAsString("usuario");
    if (usuarioNom == null || usuarioNom.isBlank()) {
      usuarioNom = jwt.getSubject();
    }
    String centroNom  = jwt.getClaimAsString("centroTrabajo");
    svc.solicitar(id, userId, centroId, usuarioNom, centroNom, dto != null ? dto.motivo() : null);
    return ResponseEntity.ok().build();
  }

  // GET: solo  confirmar
  @GetMapping(value="/anulacion/confirm", produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> confirmarPage(@RequestParam String token){
    com.washready.model.TicketAnulacion.Estado estado = svc.getEstado(token);
    
    if (estado == null) {
        return ResponseEntity.ok("""
            <!doctype html><html lang="es"><meta charset="utf-8"><title>Error</title>
            <style>*{box-sizing:border-box}body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}.wrap{max-width:460px;margin:0 auto}.box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}</style>
            <body><div class="wrap"><h2>Enlace no válido</h2><div class="box"><p>Este enlace no es válido o ya no existe.</p></div></div></body></html>
            """);
    }

    if (estado != com.washready.model.TicketAnulacion.Estado.PENDIENTE) {
        String msgName = estado == com.washready.model.TicketAnulacion.Estado.APROBADA ? "Aprobada" : (estado == com.washready.model.TicketAnulacion.Estado.RECHAZADA ? "Rechazada" : "Expirada");
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
        <title>Confirmar anulación</title>
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
          <h2>Confirmar anulación de ticket</h2>
          <div class="box">
            <p>Revisa la solicitud y confirma o rechaza la anulación.</p>
            <form method="POST" action="/api/tickets/anulacion/confirm">
              <input type="hidden" name="token" value="%s">
              <div class="actions">
                <button class="btn btn-no" type="submit" name="accion" value="RECHAZAR">Rechazar</button>
                <button class="btn btn-ok" type="submit" name="accion" value="APROBAR">Confirmar anulación</button>
              </div>
            </form>
          </div>
        </div></body></html>
        """.formatted(safeToken);
    return ResponseEntity.ok(html);
  }

  // POST: ejecuta como terminator
  @PostMapping(value="/anulacion/confirm",
               consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,
               produces=MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> confirmarDo(@RequestParam String token,
                                            @RequestParam(defaultValue="APROBAR") String accion){
    String msg = "APROBAR".equalsIgnoreCase(accion)
        ? svc.confirmar(token)
        : svc.rechazar(token);

    String html = """
        <!doctype html><html lang="es"><meta charset="utf-8">
        <title>Resultado</title>
        <style>
          *{box-sizing:border-box}
          body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,Arial,sans-serif;background:#f9fafb;color:#111;margin:0;padding:40px 16px;line-height:1.6}
          .wrap{max-width:460px;margin:0 auto}
          h2{margin:0 0 16px;font-size:1.35rem;font-weight:700}
          .box{border:1px solid #e5e7eb;border-radius:12px;padding:20px 22px;background:#fff}
          .box p{margin:0}
          .btn{display:inline-block;margin-top:16px;padding:10px 22px;border-radius:8px;font-size:.95rem;font-weight:600;text-decoration:none;background:#f3f4f6;color:#374151;border:1.5px solid #e5e7eb;transition:opacity .15s}
          .btn:hover{opacity:.8}
        </style>
        <body><div class="wrap">
          <h2>Anulación de ticket</h2>
          <div class="box"><p>%s</p></div>
          <a class="btn" href="/">← Volver a Wash &amp; Ready</a>
        </div></body></html>
        """.formatted(HtmlUtils.htmlEscape(msg));
    return ResponseEntity.ok(html);
  }

  @GetMapping("/{id}/anulacion/pendiente")
  public ResponseEntity<?> checkPendiente(@PathVariable Long id) {
    return ResponseEntity.ok(java.util.Map.of("pendiente", svc.tieneSolicitudPendiente(id)));
  }

  @GetMapping("/anulaciones/pendientes")
  public ResponseEntity<?> getPendientes(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null || !"ROLE_ADMIN".equals(jwt.getClaimAsString("role"))) {
      return ResponseEntity.status(403).build();
    }
    return ResponseEntity.ok(svc.obtenerPendientes());
  }

  @PostMapping("/anulaciones/{id}/resolver")
  public ResponseEntity<?> resolverAdmin(@AuthenticationPrincipal Jwt jwt, 
                                         @PathVariable Long id, 
                                         @RequestBody java.util.Map<String, String> payload) {
    if (jwt == null || !"ROLE_ADMIN".equals(jwt.getClaimAsString("role"))) {
      return ResponseEntity.status(403).build();
    }
    String accion = payload.get("accion");
    String token = payload.get("token");
    if (token == null) return ResponseEntity.badRequest().build();
    
    String msg = "APROBAR".equalsIgnoreCase(accion) ? svc.confirmar(token) : svc.rechazar(token);
    return ResponseEntity.ok(java.util.Map.of("message", msg));
  }
}

