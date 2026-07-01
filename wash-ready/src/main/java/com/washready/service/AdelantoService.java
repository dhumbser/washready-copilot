package com.washready.service;

import com.washready.config.AppProperties;
import com.washready.model.*;
import com.washready.pdf.PdfService;
import com.washready.pdf.TemplateRenderService;
import com.washready.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AdelantoService {

    private static final BigDecimal LIMITE_MENSUAL = new BigDecimal("300.00");
    private static final int TXT_LEN = 120;
    private static final Logger log = LoggerFactory.getLogger(AdelantoService.class);

    private final AdelantoRepository repo;
    private final UserRepository userRepo;

    private final AppProperties appProperties;
    private final EmailService emailService;
    private final ConfigCorreoService configCorreoService;
    private final TemplateRenderService templateRenderService;
    private final PdfService pdfService;

    public AdelantoService(AdelantoRepository repo,
            UserRepository userRepo,
            AppProperties appProperties,
            EmailService emailService,
            ConfigCorreoService configCorreoService,
            TemplateRenderService templateRenderService,
            PdfService pdfService) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.appProperties = appProperties;
        this.emailService = emailService;
        this.configCorreoService = configCorreoService;
        this.templateRenderService = templateRenderService;
        this.pdfService = pdfService;
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String publicBaseUrl() {
        String base = appProperties.getPublicBaseUrl();
        if (base != null && !base.isBlank()) {
            return base.replaceAll("/+$", "");
        }
        return "http://localhost:8082";
    }

    // Crear (con control de límite mensual)
    @Transactional(noRollbackFor = NotificationException.class)
    public Adelanto crear(Long userId, BigDecimal importe,
            String operarioNombre, String operarioApellido, String operarioNif) {
        if (importe == null || importe.signum() <= 0) {
            throw new IllegalArgumentException("Importe inválido");
        }
        if (importe.compareTo(LIMITE_MENSUAL) > 0) {
            throw new IllegalArgumentException("El importe máximo por solicitud es 300,00 €");
        }

        String nom = validarTexto(operarioNombre, "Nombre");
        String ape = validarTexto(operarioApellido, "Apellido");
        String nif = validarTexto(operarioNif, "NIF");

        User u = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        BigDecimal disponible = disponibleMes(userId);
        if (importe.compareTo(disponible) > 0) {
            throw new IllegalArgumentException(
                    "Límite mensual (300,00 €) alcanzado. Te quedan " + disponible.setScale(2) + " € este mes.");
        }
        LocalDateTime ahora = LocalDateTime.now();

        Adelanto a = new Adelanto();
        a.setImporte(importe);
        a.setCreadoAt(ahora);
        a.setEstado(Adelanto.Estado.PENDIENTE);
        a.setUser(u);
        a.setCentro(u.getCentroTrabajo());
        a.setOperarioNombre(nom);
        a.setOperarioApellido(ape);
        a.setOperarioNif(nif);
        a.setDecisionToken(newToken());
        int ttl = appProperties.getTokenTtlHours();
        if (ttl < 1)
            ttl = 24;
        a.setDecisionExpiraAt(ahora.plusHours(ttl));
        a = repo.save(a);

        try {
            enviarCorreo(a);
        } catch (Exception e) {
            log.error("Error enviando correo de adelanto {}: {}", a.getId(), e.getMessage(), e);
            throw new NotificationException("Solicitud guardada pero el correo no pudo enviarse: " + e.getMessage(), e);
        }

        return a;
    }

    // Búsquedas paginadas (user/admin)
    @Transactional(readOnly = true)
    public Page<Adelanto> buscar(Long userId,
            Adelanto.Estado estado,
            LocalDateTime from,
            LocalDateTime to,
            int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "creadoAt"));
        return repo.search(userId, estado, from, to, pageable);
    }

    // Decisión admin
    @Transactional
    public Adelanto decidir(Long id, Long adminId, Adelanto.Estado nuevoEstado, String motivoRechazo) {
        if (nuevoEstado == null || nuevoEstado == Adelanto.Estado.PENDIENTE) {
            throw new IllegalArgumentException("Estado inválido");
        }
        Adelanto a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Adelanto no encontrado"));
        if (a.getEstado() != Adelanto.Estado.PENDIENTE) {
            throw new IllegalStateException("La solicitud ya no está pendiente");
        }

        a.setEstado(nuevoEstado);
        a.setDecididoAt(LocalDateTime.now());
        if (nuevoEstado == Adelanto.Estado.RECHAZADO && motivoRechazo != null && !motivoRechazo.isBlank()) {
            a.setMotivoRechazo(motivoRechazo.trim());
        }
        return repo.save(a);
    }

    @Transactional(readOnly = true)
    public BigDecimal disponibleMes(Long userId) {
        LocalDate now = LocalDate.now();
        LocalDateTime from = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to = now.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        var excluidos = java.util.List.of(Adelanto.Estado.RECHAZADO, Adelanto.Estado.CANCELADO);
        BigDecimal usado = repo.sumUserInRangeExcluding(userId, excluidos, from, to);
        BigDecimal resto = LIMITE_MENSUAL.subtract(usado);
        return resto.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : resto;
    }

    // Cancelar solicitud propia (solo PENDIENTE)
    @Transactional
    public Adelanto cancelar(Long id, Long userId) {
        Adelanto a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Adelanto no encontrado"));
        if (!a.getUser().getId().equals(userId)) {
            throw new IllegalStateException("No tienes permiso para cancelar esta solicitud");
        }
        if (a.getEstado() != Adelanto.Estado.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden cancelar solicitudes pendientes");
        }
        a.setEstado(Adelanto.Estado.CANCELADO);
        a.setDecididoAt(LocalDateTime.now());
        return repo.save(a);
    }

    @Transactional
    public Adelanto.Estado getEstado(String token) {
        return repo.findByDecisionToken(token).map(Adelanto::getEstado).orElse(null);
    }

    @Transactional
    public String decidirPorToken(String token, boolean aceptar) {
        Adelanto a = repo.findByDecisionToken(token).orElseThrow(() -> new IllegalArgumentException("Token inválido"));
        LocalDateTime ahora = LocalDateTime.now();

        if (a.getEstado() != Adelanto.Estado.PENDIENTE) {
            return "Esta solicitud ya fue resuelta.";
        }
        if (a.getDecisionExpiraAt() != null && ahora.isAfter(a.getDecisionExpiraAt())) {
            return "El enlace ha expirado. Usa el panel de administración para decidir.";
        }

        a.setEstado(aceptar ? Adelanto.Estado.ACEPTADO : Adelanto.Estado.RECHAZADO);
        a.setDecididoAt(ahora);
        repo.save(a);
        return aceptar ? "Solicitud de adelanto aceptada." : "Solicitud de adelanto rechazada.";
    }

    private String validarTexto(String txt, String campo) {
        if (txt == null)
            throw new IllegalArgumentException(campo + " requerido");
        String t = txt.trim();
        if (t.isEmpty())
            throw new IllegalArgumentException(campo + " requerido");
        if (t.length() > TXT_LEN)
            t = t.substring(0, TXT_LEN);
        return t;
    }

    private void enviarCorreo(Adelanto a) {
        var to = configCorreoService.getDefaultTo()
                .orElseThrow(() -> new IllegalStateException("Config 'mail.default_to' no definida"));

        var centro = a.getCentro();
        String centroNombre = centro != null ? centro.getNombre() : "";
        String link = publicBaseUrl() + "/api/adelantos/confirm?token=" + a.getDecisionToken();

        var subject = "Solicitud de adelanto — %s %s · %s €".formatted(
                a.getOperarioNombre(), a.getOperarioApellido(),
                a.getImporte().setScale(2));
        var body = """
                <p>Se ha registrado una solicitud de adelanto que requiere tu decisión.</p>
                <ul>
                    <li><b>Trabajador:</b> %s %s</li>
                    <li><b>NIF:</b> %s</li>
                    <li><b>Importe:</b> %s €</li>
                    <li><b>Centro:</b> %s</li>
                    <li><b>Solicitud:</b> #%s</li>
                </ul>
                <p><a href="%s">Aceptar o rechazar la solicitud</a></p>
                <p style="color:#666;font-size:12px">Si el botón no funciona, copia y pega este enlace en tu navegador: %s</p>
                """.formatted(
                a.getOperarioNombre(),
                a.getOperarioApellido(),
                a.getOperarioNif(),
                a.getImporte().setScale(2),
                centroNombre,
                a.getId(),
                link,
                link);

        emailService.send(new EmailService.Email(to, subject, body, true));
    }

    @Transactional(readOnly = true)
    public byte[] printPdf(Long id, Locale locale) {
        Adelanto a = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Adelanto " + id + " no encontrado"));

        Map<String, Object> m = new HashMap<>();

        var centro = a.getCentro();
        var empresa = (centro != null) ? centro.getEmpresa() : null;

        m.put("adelantoId", a.getId());

        if (centro != null) {
            m.put("centroNombre", centro.getNombre());
            m.put("centroDireccion", centro.getDireccion());
            m.put("centroCp", centro.getCodigoPostal());
            m.put("centroCiudad", centro.getCiudad());
            m.put("centroTelefono", centro.getTelefono());
        }

        if (empresa != null) {
            m.put("empresaNombre", empresa.getNombre());
            m.put("empresaCif", empresa.getCif());
            m.put("empresaTelefono", empresa.getTelefono());
            m.put("empresaCorreo", empresa.getCorreo());
        }

        m.put("operarioNombre", a.getOperarioNombre());
        m.put("operarioApellido", a.getOperarioApellido());
        m.put("operarioNif", a.getOperarioNif());
        m.put("importe", a.getImporte());
        m.put("estado", a.getEstado());
        m.put("fechaSolicitud", a.getCreadoAt());
        String solicitanteNombre = a.getOperarioNombre() != null ? a.getOperarioNombre().trim() : "";
        String solicitanteApellido = a.getOperarioApellido() != null ? a.getOperarioApellido().trim() : "";
        String solicitante = (solicitanteNombre + " " + solicitanteApellido).trim();
        m.put("solicitadoPor", solicitante);
        m.put("decididoAt", a.getDecididoAt());
        m.put("motivoRechazo", a.getMotivoRechazo());
        m.put("generadoAt", LocalDateTime.now());

        String html = templateRenderService.render("adelantos/adelanto_pdf", m, locale);
        return pdfService.htmlToPdf(html);
    }

    public static class NotificationException extends RuntimeException {
        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
