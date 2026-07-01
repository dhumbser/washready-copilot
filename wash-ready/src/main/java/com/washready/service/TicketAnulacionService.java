package com.washready.service;

import com.washready.config.AppProperties;
import com.washready.model.CentroTrabajo;
import com.washready.model.Cliente;
import com.washready.model.Ticket;
import com.washready.model.TicketAnulacion;
import com.washready.model.TicketAnulacion.Estado;
import com.washready.repository.TicketAnulacionRepository;
import com.washready.repository.TicketRepository;
import com.washready.repository.ClienteRepository;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.UserRepository;
import com.washready.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class TicketAnulacionService {

  private final TicketRepository ticketRepo;
  private final TicketAnulacionRepository anulRepo;
  private final ClienteRepository clienteRepo;
  private final CentroTrabajoRepository centroRepo;
  private final UserRepository userRepo;
  private final EmailService emailService;
  private final ConfigCorreoService configCorreoService;
  private final AppProperties props;

  private static final Logger log = LoggerFactory.getLogger(TicketAnulacionService.class);

  public TicketAnulacionService(
      TicketRepository ticketRepo,
      TicketAnulacionRepository anulRepo,
      ClienteRepository clienteRepo,
      CentroTrabajoRepository centroRepo,
      UserRepository userRepo,
      EmailService emailService,
      ConfigCorreoService configCorreoService,
      AppProperties props) {
    this.ticketRepo = ticketRepo;
    this.anulRepo = anulRepo;
    this.clienteRepo = clienteRepo;
    this.centroRepo = centroRepo;
    this.userRepo = userRepo;
    this.emailService = emailService;
    this.configCorreoService = configCorreoService;
    this.props = props;
  }

  private String newToken() {
    byte[] b = new byte[24];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  /** Intenta obtener "Nombre Apellido" del cliente del ticket. */
  private String resolveClienteNombre(Ticket t) {
    try {
      Cliente cliente = t.getCliente(); // asumiendo getter estándar en Ticket
      if (cliente == null)
        return null;

      Long clienteId = cliente.getId();
      if (clienteId != null) {
        return clienteRepo.findById(clienteId)
            .map(c -> ((c.getNombre() == null ? "" : c.getNombre()) + " " +
                (c.getApellido() == null ? "" : c.getApellido())).trim())
            .filter(s -> !s.isBlank())
            .orElse("#" + clienteId);
      } else {
        String name = ((cliente.getNombre() == null ? "" : cliente.getNombre()) + " " +
            (cliente.getApellido() == null ? "" : cliente.getApellido())).trim();
        return name.isBlank() ? null : name;
      }
    } catch (Exception ignored) {
      return null;
    }
  }

  private String resolveCentroNombre(Ticket t, Long centroId, String centroNom) {
    if (centroNom != null && !centroNom.isBlank())
      return centroNom;

    try {

      if (centroId != null) {
        return centroRepo.findById(centroId)
            .map(CentroTrabajo::getNombre)
            .filter(n -> n != null && !n.isBlank())
            .orElse("Centro " + centroId);
      }

      Object tCentro = t.getCentro();
      if (tCentro instanceof CentroTrabajo) {
        CentroTrabajo c = (CentroTrabajo) tCentro;
        String name = c.getNombre();
        if (name != null && !name.isBlank())
          return name;
        Long id = c.getId();
        if (id != null) {
          return centroRepo.findById(id)
              .map(CentroTrabajo::getNombre)
              .filter(n -> n != null && !n.isBlank())
              .orElse("Centro " + id);
        }
      } else if (tCentro instanceof Long) {
        Long id = (Long) tCentro;
        return centroRepo.findById(id)
            .map(CentroTrabajo::getNombre)
            .filter(n -> n != null && !n.isBlank())
            .orElse("Centro " + id);
      }
    } catch (Exception ignored) {
    }

    return "Centro";
  }

  @Transactional
  public void anularDirecto(Long ticketId) {
    Ticket t = ticketRepo.findById(ticketId)
        .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));
    if (t.getEstado() != Ticket.Estado.PTE_PAGO && t.getEstado() != Ticket.Estado.PAGADO)
      throw new IllegalStateException("Solo se puede anular desde PTE_PAGO o PAGADO");

    t.setEstado(Ticket.Estado.ANULADO);
    ticketRepo.save(t);

    LocalDateTime now = LocalDateTime.now();
    List<TicketAnulacion> pendientes = anulRepo.findByTicketAndEstado(t, Estado.PENDIENTE);
    for (TicketAnulacion a : pendientes) {
      a.setEstado(Estado.APROBADA);
      a.setResueltoAt(now);
    }
    if (!pendientes.isEmpty()) anulRepo.saveAll(pendientes);
  }

  @Transactional
  public void solicitar(Long ticketId, Long userId, Long centroId,
      String usuarioNom, String centroNom, String motivo) {

    Ticket t = ticketRepo.findById(ticketId)
        .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));

    if (t.getEstado() != Ticket.Estado.PTE_PAGO && t.getEstado() != Ticket.Estado.PAGADO) {
      throw new IllegalStateException("Sólo se puede solicitar anulación desde PTE_PAGO o PAGADO");
    }

    // Expirar solicitudes previas pendientes del mismo ticket
    List<TicketAnulacion> pendientes = anulRepo.findByTicketAndEstado(t, Estado.PENDIENTE);
    LocalDateTime now = LocalDateTime.now();
    for (TicketAnulacion a : pendientes) {
      a.setEstado(Estado.EXPIRADA);
      a.setResueltoAt(now);
    }
    if (!pendientes.isEmpty())
      anulRepo.saveAll(pendientes);

    // Nueva solicitud
    TicketAnulacion ta = new TicketAnulacion();
    ta.setTicket(t);
    ta.setUserId(userId != null ? userId : 0L);
    ta.setCentroId(centroId != null ? centroId : 0L);
    ta.setMotivo(motivo != null ? (motivo.length() > 300 ? motivo.substring(0, 300) : motivo) : "");
    ta.setToken(newToken());
    ta.setCreadoAt(now);

    // TTL desde propiedades (fallback 24h)
    int ttlHours = props.getTokenTtlHours();
    if (ttlHours < 1) ttlHours = 24;
    ta.setExpiraAt(now.plusHours(ttlHours));
    ta.setEstado(Estado.PENDIENTE);
    ta = anulRepo.save(ta);

    // Enlace mágico
    String base = (props.getPublicBaseUrl() != null && !props.getPublicBaseUrl().isBlank())
        ? props.getPublicBaseUrl().replaceAll("/+$", "")
        : "http://localhost:8082";
    String link = base + "/api/tickets/anulacion/confirm?token=" + ta.getToken();

    String ref = (t.getReferencia() != null && !t.getReferencia().isBlank()) ? t.getReferencia() : ("#" + t.getId());
    String centro = resolveCentroNombre(t, centroId, centroNom);
    String user = (usuarioNom != null && !usuarioNom.isBlank()) ? usuarioNom : ("user:" + ta.getUserId());
    String cliente = resolveClienteNombre(t);

    try {
      enviarCorreo(ref, centro, user, cliente, motivo, link);
    } catch (Exception e) {
      log.warn("No se pudo enviar el correo de solicitud de anulación (ticketId={}): {}", ticketId, e.getMessage());
    }
  }

  private void enviarCorreo(String ref, String centro, String usuario, String cliente, String motivo, String link) {
    var to = configCorreoService.getDefaultTo()
        .orElseThrow(() -> new IllegalStateException("Config 'mail.default_to' no definida"));

    var subject = "Solicitud de anulación de ticket — " + ref;
    var body = """
            <p>Se ha solicitado la anulación de un ticket y requiere tu decisión.</p>
            <ul>
                <li><b>Ticket:</b> %s</li>
                %s
                %s
                <li><b>Solicitado por:</b> %s</li>
                <li><b>Centro:</b> %s</li>
            </ul>
            <p><a href="%s">Confirmar o rechazar la anulación</a></p>
            <p style="color:#666;font-size:12px">Si el botón no funciona, copia y pega este enlace en tu navegador: %s</p>
            """.formatted(
            ref,
            cliente != null ? "<li><b>Cliente:</b> " + cliente + "</li>" : "",
            (motivo != null && !motivo.isBlank()) ? "<li><b>Motivo:</b> " + motivo + "</li>" : "",
            usuario,
            centro,
            link,
            link);

    emailService.send(new EmailService.Email(to, subject, body, true));
  }

  public Estado getEstado(String token) {
    return anulRepo.findByToken(token).map(TicketAnulacion::getEstado).orElse(null);
  }

  @Transactional
  public String confirmar(String token) {
    TicketAnulacion ta = anulRepo.findByToken(token)
        .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

    LocalDateTime now = LocalDateTime.now();

    if (ta.getEstado() != Estado.PENDIENTE) {
      return "Este enlace ya fue usado o no está disponible.";
    }
    if (now.isAfter(ta.getExpiraAt())) {
      ta.setEstado(Estado.EXPIRADA);
      ta.setResueltoAt(now);
      anulRepo.save(ta);
      return "El enlace ha expirado.";
    }

    Ticket t = ta.getTicket();
    if (t.getEstado() != Ticket.Estado.ANULADO) {
      t.setEstado(Ticket.Estado.ANULADO);
      ticketRepo.save(t);
    }

    ta.setEstado(Estado.APROBADA);
    ta.setResueltoAt(now);
    anulRepo.save(ta);

    return "Ticket anulado correctamente.";
  }

  @Transactional
  public String rechazar(String token) {
    TicketAnulacion ta = anulRepo.findByToken(token)
        .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

    LocalDateTime now = LocalDateTime.now();

    if (ta.getEstado() != TicketAnulacion.Estado.PENDIENTE) {
      return "Este enlace ya fue usado o no está disponible.";
    }
    if (now.isAfter(ta.getExpiraAt())) {
      ta.setEstado(TicketAnulacion.Estado.EXPIRADA);
      ta.setResueltoAt(now);
      anulRepo.save(ta);
      return "El enlace ha expirado.";
    }

    // NO tocamos el ticket
    ta.setEstado(TicketAnulacion.Estado.RECHAZADA);
    ta.setResueltoAt(now);
    anulRepo.save(ta);

    return "Solicitud de anulación rechazada.";
  }

  public boolean tieneSolicitudPendiente(Long ticketId) {
    if (ticketId == null)
      return false;
    return anulRepo.existsByTicketIdAndEstado(ticketId, Estado.PENDIENTE);
  }

  public record AnulacionPendienteDTO(
      Long ticketId,
      String referencia,
      String centroNombre,
      String operarioNombre,
      String motivo,
      LocalDateTime creadoAt,
      String token) {
  }

  public List<AnulacionPendienteDTO> obtenerPendientes() {
    List<TicketAnulacion> pendientes = anulRepo.findAllByEstadoOrderByCreadoAtDesc(Estado.PENDIENTE);
    return pendientes.stream().map(a -> {
      Ticket t = a.getTicket();
      String ref = (t.getReferencia() != null && !t.getReferencia().isBlank()) ? t.getReferencia() : ("#" + t.getId());
      String centroNom = resolveCentroNombre(t, a.getCentroId(), null);

      String operario = "Usuario ID: " + a.getUserId();
      if (a.getUserId() != null && a.getUserId() > 0) {
        operario = userRepo.findById(a.getUserId())
            .map(User::getUsuario)
            .filter(n -> n != null && !n.isBlank())
            .orElse(operario);
      }

      return new AnulacionPendienteDTO(
          t.getId(),
          ref,
          centroNom,
          operario,
          a.getMotivo(),
          a.getCreadoAt(),
          a.getToken());
    }).toList();
  }

}