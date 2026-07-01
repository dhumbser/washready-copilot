package com.washready.service;

import com.washready.config.AppProperties;
import com.washready.model.Cliente;
import com.washready.model.ClienteNoDeseadoSolicitud;
import com.washready.model.ClienteNoDeseadoSolicitud.Estado;
import com.washready.model.CentroTrabajo;
import com.washready.model.User;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.ClienteNoDeseadoSolicitudRepository;
import com.washready.repository.ClienteRepository;
import com.washready.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ClienteNoDeseadoService {

    private static final Logger log = LoggerFactory.getLogger(ClienteNoDeseadoService.class);

    private final ClienteRepository clienteRepo;
    private final ClienteNoDeseadoSolicitudRepository repo;
    private final CentroTrabajoRepository centroRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final ConfigCorreoService configCorreoService;
    private final AppProperties props;

    public ClienteNoDeseadoService(ClienteRepository clienteRepo,
                                   ClienteNoDeseadoSolicitudRepository repo,
                                   CentroTrabajoRepository centroRepo,
                                   UserRepository userRepo,
                                   EmailService emailService,
                                   ConfigCorreoService configCorreoService,
                                   AppProperties props) {
        this.clienteRepo = clienteRepo;
        this.repo = repo;
        this.centroRepo = centroRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.configCorreoService = configCorreoService;
        this.props = props;
    }

    @Transactional
    public void marcarDirecto(Long clienteId) {
        Cliente c = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (c.isNoDeseado()) throw new IllegalStateException("El cliente ya está marcado como no deseado");
        c.setNoDeseado(true);
        clienteRepo.save(c);
    }

    @Transactional
    public void solicitar(Long clienteId, Long userId, Long centroId, String usuarioNom, String centroNom, String motivo) {
        Cliente c = clienteRepo.findById(clienteId).orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        if (c.isNoDeseado()) {
            throw new IllegalStateException("El cliente ya está marcado como no deseado");
        }

        // Expirar solicitudes previas pendientes del mismo cliente
        List<ClienteNoDeseadoSolicitud> pendientes = repo.findByClienteAndEstado(c, Estado.PENDIENTE);
        LocalDateTime now = LocalDateTime.now();
        for (ClienteNoDeseadoSolicitud s : pendientes) {
            s.setEstado(Estado.EXPIRADA);
            s.setResueltoAt(now);
        }
        if (!pendientes.isEmpty()) repo.saveAll(pendientes);

        ClienteNoDeseadoSolicitud s = new ClienteNoDeseadoSolicitud();
        s.setCliente(c);
        s.setUserId(userId != null ? userId : 0L);
        s.setCentroId(centroId != null ? centroId : 0L);
        s.setMotivo(normalizeMotivo(motivo));
        s.setToken(newToken());
        s.setCreadoAt(now);

        int ttlHours = props.getTokenTtlHours();
        if (ttlHours < 1) ttlHours = 24;
        s.setExpiraAt(now.plusHours(ttlHours));
        s.setEstado(Estado.PENDIENTE);
        s = repo.save(s);

        try {
            enviarCorreo(s, usuarioNom, centroNom);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de solicitud no-deseado (id={}): {}", s.getId(), e.getMessage());
        }
    }

    @Transactional
    public String confirmar(String token) {
        ClienteNoDeseadoSolicitud s = repo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));
        LocalDateTime now = LocalDateTime.now();

        if (s.getEstado() != Estado.PENDIENTE) {
            return "Este enlace ya fue usado o no está disponible.";
        }
        if (now.isAfter(s.getExpiraAt())) {
            s.setEstado(Estado.EXPIRADA);
            s.setResueltoAt(now);
            repo.save(s);
            return "El enlace ha expirado.";
        }

        Cliente c = s.getCliente();
        c.setNoDeseado(true);
        clienteRepo.save(c);

        s.setEstado(Estado.APROBADA);
        s.setResueltoAt(now);
        repo.save(s);
        return "Cliente marcado como No deseado.";
    }

    @Transactional
    public String rechazar(String token) {
        ClienteNoDeseadoSolicitud s = repo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));
        LocalDateTime now = LocalDateTime.now();

        if (s.getEstado() != Estado.PENDIENTE) {
            return "Este enlace ya fue usado o no está disponible.";
        }
        if (now.isAfter(s.getExpiraAt())) {
            s.setEstado(Estado.EXPIRADA);
            s.setResueltoAt(now);
            repo.save(s);
            return "El enlace ha expirado.";
        }

        s.setEstado(Estado.RECHAZADA);
        s.setResueltoAt(now);
        repo.save(s);
        return "Solicitud de No deseado rechazada.";
    }

    @Transactional(readOnly = true)
    public boolean tieneSolicitudPendiente(Long clienteId) {
        Cliente c = clienteRepo.findById(clienteId).orElse(null);
        if (c == null) return false;
        return !repo.findByClienteAndEstado(c, Estado.PENDIENTE).isEmpty();
    }

    private void enviarCorreo(ClienteNoDeseadoSolicitud s, String usuarioNom, String centroNom) {
        var to = configCorreoService.getDefaultTo()
                .orElseThrow(() -> new IllegalStateException("Config 'mail.default_to' no definida"));

        String base = (props.getPublicBaseUrl() != null && !props.getPublicBaseUrl().isBlank())
                ? props.getPublicBaseUrl().replaceAll("/+$", "")
                : "http://localhost:8082";
        String link = base + "/api/clientes/no-deseado/confirm?token=" + s.getToken();

        Cliente c = s.getCliente();
        String cli = (c != null)
                ? String.format("%s %s", safe(c.getNombre()), safe(c.getApellido())).trim()
                : "—";
        String centro = resolveCentroNombre(centroNom, s.getCentroId());
        String usuario = (usuarioNom != null && !usuarioNom.isBlank()) ? usuarioNom : ("user:" + s.getUserId());

        var subject = "Solicitud de cliente No deseado — " + (cli.isBlank() ? "—" : cli);
        var body = """
                <p>Se ha solicitado marcar a un cliente como No deseado y requiere tu decisión.</p>
                <ul>
                    <li><b>Cliente:</b> %s</li>
                    <li><b>Motivo:</b> %s</li>
                    <li><b>Solicitado por:</b> %s</li>
                    <li><b>Centro:</b> %s</li>
                </ul>
                <p><a href="%s">Confirmar o rechazar la solicitud</a></p>
                <p style="color:#666;font-size:12px">Si el botón no funciona, copia y pega este enlace en tu navegador: %s</p>
                """.formatted(
                cli.isBlank() ? "—" : cli,
                s.getMotivo(),
                usuario,
                centro,
                link,
                link);

        emailService.send(new EmailService.Email(to, subject, body, true));
    }

    public Estado getEstado(String token) {
        return repo.findByToken(token).map(ClienteNoDeseadoSolicitud::getEstado).orElse(null);
    }

    public record NoDeseadoPendienteDTO(
            Long solicitudId,
            Long clienteId,
            String clienteNombre,
            String centroNombre,
            String solicitadoPor,
            String motivo,
            java.time.LocalDateTime creadoAt,
            String token) {}

    public java.util.List<NoDeseadoPendienteDTO> obtenerPendientes() {
        return repo.findAllByEstadoOrderByCreadoAtDesc(Estado.PENDIENTE).stream().map(s -> {
            Cliente c = s.getCliente();
            String clienteNom = c != null
                    ? String.format("%s %s", safe(c.getNombre()), safe(c.getApellido())).trim()
                    : "—";
            String centro = resolveCentroNombre(null, s.getCentroId());
            String solicitadoPor = "user:" + s.getUserId();
            if (s.getUserId() != null && s.getUserId() > 0) {
                solicitadoPor = userRepo.findById(s.getUserId())
                        .map(User::getUsuario)
                        .filter(n -> n != null && !n.isBlank())
                        .orElse(solicitadoPor);
            }
            return new NoDeseadoPendienteDTO(
                    s.getId(),
                    c != null ? c.getId() : null,
                    clienteNom.isBlank() ? "—" : clienteNom,
                    centro,
                    solicitadoPor,
                    s.getMotivo(),
                    s.getCreadoAt(),
                    s.getToken());
        }).toList();
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }

    private String resolveCentroNombre(String centroNom, Long centroId) {
        if (centroNom != null && !centroNom.isBlank()) return centroNom;
        if (centroId != null && centroId > 0) {
            return centroRepo.findById(centroId)
                    .map(CentroTrabajo::getNombre)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse("Centro " + centroId);
        }
        return "—";
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizeMotivo(String motivo) {
        if (motivo == null) throw new IllegalArgumentException("El motivo es obligatorio");
        String t = motivo.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("El motivo es obligatorio");
        if (t.length() > 300) t = t.substring(0, 300);
        return t;
    }
}
