package com.washready.service;

import com.washready.model.Cliente;
import com.washready.model.Servicio;
import com.washready.model.Ticket;
import com.washready.model.TicketDetalle;
import com.washready.model.User;
import com.washready.model.Vehiculo;
import com.washready.pdf.PdfService;
import com.washready.pdf.TemplateRenderService;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.ClienteRepository;
import com.washready.repository.ServicioRepository;
import com.washready.repository.TicketDetalleRepository;
import com.washready.repository.TicketRepository;
import com.washready.repository.UserRepository;
import com.washready.repository.VehiculoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import com.washready.util.FechasUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TicketService {

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;
    private final ClienteRepository clienteRepo;
    private final VehiculoRepository vehiculoRepo;
    private final TicketDetalleRepository detalleRepo;
    private final ServicioRepository servicioRepo;
    private final CentroTrabajoRepository centroRepo;
    private final TemplateRenderService templateRenderService;
    private final PdfService pdfService;
    public TicketService(TicketRepository ticketRepo,
            UserRepository userRepo,
            ClienteRepository clienteRepo,
            VehiculoRepository vehiculoRepo,
            TicketDetalleRepository detalleRepo,
            ServicioRepository servicioRepo,
            CentroTrabajoRepository centroRepo,
            TemplateRenderService templateRenderService,
            PdfService pdfService) {
        this.ticketRepo = ticketRepo;
        this.userRepo = userRepo;
        this.clienteRepo = clienteRepo;
        this.vehiculoRepo = vehiculoRepo;
        this.detalleRepo = detalleRepo;
        this.servicioRepo = servicioRepo;
        this.centroRepo = centroRepo;
        this.pdfService = pdfService;
        this.templateRenderService = templateRenderService;
    }

    // ===== Comando que llega desde el controller (DTO de servicio) =====
    // Añadida descripcion (snapshot/override)
    public record DetalleCommand(Long servicioId, Integer cantidad, BigDecimal precio, String descripcion) {
        // compat: permite seguir llamando con 3 args desde código viejo
        public DetalleCommand(Long servicioId, Integer cantidad, BigDecimal precio) {
            this(servicioId, cantidad, precio, null);
        }
    }

    // ===== Lecturas =====
    @Transactional(readOnly = true)
    public List<Ticket> listar() {
        return ticketRepo.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> obtener(Long id) {
        return ticketRepo.findById(id);
    }

    // ===== Helpers referencia anual =====
    private static String prefixFromCentro(String nombreCentro) {
        if (nombreCentro == null)
            nombreCentro = "";
        String s = Normalizer.normalize(nombreCentro, Normalizer.Form.NFD)
                .replaceAll("[^\\p{IsAlphabetic}]", "")
                .toUpperCase();
        if (s.length() < 3)
            s = (s + "XXX").substring(0, 3);
        return s.substring(0, 3);
    }

    private static int readSuffixFromRef(String ref) {
        if (ref == null)
            return 0;
        int i = ref.lastIndexOf('-');
        if (i < 0 || i + 1 >= ref.length())
            return 0;
        String digits = ref.substring(i + 1).replaceAll("\\D", "");
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    // ===== Altas/Actualizaciones =====
    @Transactional(readOnly = true)
    public String nextReferencia(Long centroId, Instant fecha) {
        var centro = centroRepo.findById(centroId)
                .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado: " + centroId));

        var ld = (fecha != null ? fecha : Instant.now()).atZone(FechasUtil.ZONE).toLocalDate();
        var start = ld.withDayOfYear(1).atStartOfDay(FechasUtil.ZONE).toInstant();
        var end = ld.withDayOfYear(1).plusYears(1).atStartOfDay(FechasUtil.ZONE).toInstant();

        var lastOpt = ticketRepo
                .findTopByCentroIdAndFechaBetweenAndReferenciaIsNotNullOrderByIdDesc(centroId, start, end);

        int next = lastOpt.map(Ticket::getReferencia)
                .map(TicketService::readSuffixFromRef)
                .orElse(0) + 1;

        String pref = prefixFromCentro(centro.getNombre());
        int yy = ld.getYear() % 100;
        return String.format("%s%d-%02d-%05d", pref, centroId, yy, next);
    }

    // Compat: método viejo (sin admin) -> llama al nuevo
    @Transactional
    public Ticket crear(Ticket t, Long usuarioId, Long clienteId, Long vehiculoId, List<DetalleCommand> detalles) {
        return crear(t, usuarioId, clienteId, vehiculoId, detalles, false);
    }

    @Transactional
    public Ticket crear(Ticket t, Long usuarioId, Long clienteId, Long vehiculoId,
            List<DetalleCommand> detalles, boolean admin) {
        User u = userRepo.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        Cliente c = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        Vehiculo v = vehiculoRepo.findById(vehiculoId)
                .orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));

        if (t.getFecha() == null)
            t.setFecha(Instant.now());
        if (t.getEstado() == null)
            t.setEstado(Ticket.Estado.PTE_PAGO);
        if (t.getIva() == null)
            t.setIva(new BigDecimal("21.00"));

        // t.setPlaza(t.getPlaza());
        t.setUsuario(u);
        t.setCliente(c);
        t.setVehiculo(v);

        if (t.getBonoMotivo() != null)
            t.setBonoMotivo(t.getBonoMotivo().trim());

        var centro = u.getCentroTrabajo();
        if (centro == null)
            throw new IllegalArgumentException("Usuario sin centro de trabajo");
        t.setCentro(centro);

        // 1) Guardamos para tener ID
        t = ticketRepo.save(t);

        // 2) Líneas (si vienen)
        if (detalles != null && !detalles.isEmpty()) {
            for (DetalleCommand d : detalles) {
                agregarDetalle(t, d, admin);
            }
        }

        // 3) Referencia AAA<centroId>-YY-##### (reset anual por fecha)
        LocalDate ld = t.getFecha().atZone(FechasUtil.ZONE).toLocalDate();
        Instant start = ld.withDayOfYear(1).atStartOfDay(FechasUtil.ZONE).toInstant();
        Instant end = ld.withDayOfYear(1).plusYears(1).atStartOfDay(FechasUtil.ZONE).toInstant();
        final String pref = prefixFromCentro(centro.getNombre());
        final long centroId = centro.getId();
        final int yy = ld.getYear() % 100;

        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<Ticket> lastOpt = ticketRepo.findTopByCentroIdAndFechaBetweenAndReferenciaIsNotNullOrderByIdDesc(
                    centroId, start, end);

            int next = lastOpt.map(Ticket::getReferencia)
                    .map(TicketService::readSuffixFromRef)
                    .orElse(0) + 1;

            String ref = String.format("%s%d-%02d-%05d", pref, centroId, yy, next);
            t.setReferencia(ref);

            try {
                return recomputarYGuardar(t); // guarda totales + referencia
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                if (attempt == 4)
                    throw dup;
            }
        }
        // fallback (no debería llegar)
        return recomputarYGuardar(t);
    }

    // Compat: método viejo (sin admin) -> llama al nuevo
    @Transactional
    public Optional<Ticket> actualizarConDetalles(Long id, Ticket data, Long usuarioId, Long clienteId,
            Long vehiculoId, List<DetalleCommand> nuevos) {
        return actualizarConDetalles(id, data, usuarioId, clienteId, vehiculoId, nuevos, false);
    }

    @Transactional
    public Optional<Ticket> actualizarConDetalles(Long id, Ticket data, Long usuarioId, Long clienteId,
            Long vehiculoId, List<DetalleCommand> nuevos, boolean admin) {
        return ticketRepo.findById(id).map(t -> {
            if (data.getFecha() != null)
                t.setFecha(data.getFecha());
            if (data.getComentarios() != null)
                t.setComentarios(data.getComentarios());
            if (data.getBonoMotivo() != null)
                t.setBonoMotivo(data.getBonoMotivo());
            if (data.getPlaza() != null)
                t.setPlaza(data.getPlaza());
            if (data.getEstado() != null)
                t.setEstado(data.getEstado());
            if (data.getMetodoPago() != null)
                t.setMetodoPago(data.getMetodoPago());
            if (data.getIva() != null)
                t.setIva(data.getIva());

            if (usuarioId != null) {
                t.setUsuario(userRepo.findById(usuarioId)
                        .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado")));
            }
            if (clienteId != null) {
                t.setCliente(clienteRepo.findById(clienteId)
                        .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado")));
            }
            if (vehiculoId != null) {
                t.setVehiculo(vehiculoRepo.findById(vehiculoId)
                        .orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado")));
            }

            // Si nos pasan lista de detalles -> reemplazo completo
            if (nuevos != null) {
                var actuales = detalleRepo.findByTicketId(t.getId());
                if (!actuales.isEmpty())
                    detalleRepo.deleteAll(actuales);
                for (DetalleCommand d : nuevos) {
                    agregarDetalle(t, d, admin);
                }
            }

            return recomputarYGuardar(t);
        });
    }

    @Transactional
    public boolean eliminar(Long id) {
        return ticketRepo.findById(id).map(t -> {
            ticketRepo.delete(t);
            return true;
        }).orElse(false);
    }

    // ===== Recalcular totales desde ticket_detalle =====
    @Transactional
    public Ticket recomputarTotales(Long ticketId) {
        Ticket t = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado"));
        return recomputarYGuardar(t);
    }

    private static String norm(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    // ===== Búsquedas/paginación de Tabla =====
    @Transactional(readOnly = true)
    public Page<Ticket> buscar(
            Long centroId,
            String matricula, String marca, String modelo, String color,
            String cliente, String telefono,
            String operario, String referencia,
            List<Ticket.Estado> estados, Ticket.Estado estado,
            Instant desde, Instant hasta,
            Ticket.MetodoPago metodo,
            Pageable pageable) {

        return ticketRepo.search(
                centroId,
                norm(matricula), norm(marca), norm(modelo), norm(color),
                norm(cliente), norm(telefono),
                norm(operario), norm(referencia),
                (estados != null && !estados.isEmpty()) ? estados : null,
                estado,
                desde, hasta,
                metodo,
                pageable);
    }

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private Ticket recomputarYGuardar(Ticket t) {
        var detalles = detalleRepo.findByTicketId(t.getId());

        // 1) Sumar líneas (precio * cantidad) = TOTAL (con IVA)
        BigDecimal total = BigDecimal.ZERO;
        for (var d : detalles) {
            if (d.getPrecio() != null && d.getCantidad() != null) {
                BigDecimal line = d.getPrecio().multiply(BigDecimal.valueOf(d.getCantidad()));
                total = total.add(line);
            }
        }
        // Redondeo final del total
        total = total.setScale(2, RoundingMode.HALF_UP);
        t.setTotal(total);

        // IVA% (por defecto 21)
        BigDecimal ivaPct = (t.getIva() != null) ? t.getIva() : new BigDecimal("21.00");
        t.setIva(ivaPct);

        // Base imponible = total / (1 + iva%)
        BigDecimal factor = BigDecimal.ONE.add(
                ivaPct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal base = total.divide(factor, 6, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        t.setTotalSinIva(base);

        // log por si acaso
        log.debug("Ticket {} => total:{}, iva%:{}, factor:{}, base:{}",
                t.getId(), total, ivaPct, factor, base);

        return ticketRepo.save(t);
    }

    // ===== Helpers =====

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static Integer safeQty(Integer q) {
        if (q == null)
            return 1;
        return (q > 0) ? q : 1;
    }

    // Ahora guarda snapshot de descripción y controla override editable/admin
    private void agregarDetalle(Ticket t, DetalleCommand d, boolean admin) {
        if (d == null || d.servicioId() == null) {
            throw new IllegalArgumentException("Detalle inválido (servicioId requerido)");
        }

        Servicio servicio = servicioRepo.findById(d.servicioId())
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado: " + d.servicioId()));

        boolean wantsOverride = hasText(d.descripcion()) || d.precio() != null;

        // Regla clara: override SOLO si el servicio es editable o admin
        boolean editable = false;
        try {
            editable = servicio.isEditable();
        } catch (Exception ignored) {
            // si tu entidad aún no tiene isEditable(), aquí te explotará en compile, no en
            // runtime.
        }

        if (wantsOverride && !editable && !admin) {
            throw new IllegalArgumentException("Este servicio no permite edición en el ticket");
        }

        String desc = hasText(d.descripcion())
                ? d.descripcion().trim()
                : (hasText(servicio.getDescripcion()) ? servicio.getDescripcion().trim() : ("Servicio #" + servicio.getId()));

        BigDecimal unit = (d.precio() != null) ? d.precio() : servicio.getImporte();
        if (unit == null)
            unit = BigDecimal.ZERO;
        unit = unit.setScale(2, RoundingMode.HALF_UP);

        TicketDetalle det = new TicketDetalle();
        det.setTicket(t);
        det.setServicio(servicio);
        det.setCantidad(safeQty(d.cantidad()));
        det.setPrecio(unit);

        // Snapshot de descripción en la línea del ticket
        det.setDescripcionServicio(desc);

        detalleRepo.save(det);
    }

    // ===== Estado: marcar como pagado =====
    @Transactional
    public Optional<Ticket> marcarComoPagado(Long id, Ticket.MetodoPago metodo, String bonoMotivo, boolean isAdmin) {
        var opt = ticketRepo.findById(id);
        if (opt.isEmpty())
            return Optional.empty();

        var t = opt.get();
        if (t.getEstado() != Ticket.Estado.PTE_PAGO) {
            throw new IllegalArgumentException("Solo se puede pagar un ticket en estado 'pte. de pago'");
        }
        if (metodo == null) {
            throw new IllegalArgumentException("Método de pago requerido");
        }

        if (metodo == Ticket.MetodoPago.BONO) {
            String motivo = (bonoMotivo != null && !bonoMotivo.isBlank()) ? bonoMotivo.trim() : t.getBonoMotivo();
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalArgumentException("Indica el motivo del bono");
            }
            t.setBonoMotivo(motivo);
        } else {
            t.setBonoMotivo(null);
        }

        t.setMetodoPago(metodo);
        t.setEstado(Ticket.Estado.PAGADO);
        return Optional.of(ticketRepo.save(t));
    }

    @Transactional
    public Optional<Ticket> marcarComoEntregado(Long id) {
        return ticketRepo.findById(id).map(t -> {
            if (t.getEstado() != Ticket.Estado.PAGADO) {
                throw new IllegalArgumentException("Solo se puede entregar un ticket en estado 'pagado'");
            }
            t.setEstado(Ticket.Estado.CERRADO);
            return ticketRepo.save(t);
        });
    }

    @Transactional(readOnly = true)
    public byte[] printPdf(Long id, Locale locale) {
        Ticket t = ticketRepo.findByIdDeep(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket " + id + " no encontrado"));
        // Modelo para la plantilla
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("ticket", t);

        try (var is = getClass().getResourceAsStream("/static/img/logo.png")) {
            if (is != null) {
                String b64 = Base64.getEncoder().encodeToString(is.readAllBytes());
                m.put("logoUrl", "data:image/png;base64," + b64);
            }
        } catch (Exception ignored) { }

        var ct = (t.getUsuario() != null) ? t.getUsuario().getCentroTrabajo() : null;
        var e = (ct != null) ? ct.getEmpresa() : null;
        // Cabecera del PDF: datos del CENTRO
        if (ct != null) {
            m.put("centroNombre", ct.getNombre());
            m.put("centroDireccion", ct.getDireccion());
            m.put("centroCp", ct.getCodigoPostal());
            m.put("centroCiudad", ct.getCiudad());
            Boolean showLogo = ct.getMostrarLogoTicket();
            m.put("centroMostrarLogo", showLogo == null ? Boolean.TRUE : showLogo);
        }
        m.put("empresaNombre", (e != null) ? e.getNombre() : "");
        m.put("empresaCif", (e != null) ? e.getCif() : "");

        m.put("referencia", t.getReferencia() != null ? t.getReferencia() : String.valueOf(t.getId()));
        m.put("fecha", t.getFecha() != null ? t.getFecha().atZone(FechasUtil.ZONE).toLocalDate() : null);
        if (t.getCliente() != null) {
            var cli = t.getCliente();
            String apellido = cli.getApellido();
            String nombre = cli.getNombre() != null ? cli.getNombre() : "";
            m.put("clienteNombre", apellido != null ? (nombre + " " + apellido).trim() : nombre);
        } else {
            m.put("clienteNombre", "");
        }
        m.put("clienteTelefono", t.getCliente() != null ? t.getCliente().getTelefono() : "");
        m.put("matricula", t.getVehiculo() != null ? t.getVehiculo().getMatricula() : "");
        m.put("color", t.getVehiculo() != null ? t.getVehiculo().getColor() : "");
        m.put("marca", t.getVehiculo() != null ? t.getVehiculo().getMarca() : "");
        m.put("modelo", t.getVehiculo() != null ? t.getVehiculo().getModelo() : "");
        m.put("plaza", t.getVehiculo() != null ? t.getVehiculo().getPlaza() : null);

        m.put("total", t.getTotal());
        BigDecimal totalVisible = BigDecimal.ZERO;
        if (t.getDetalles() != null) {
            for (var d : t.getDetalles()) {
                if (d.getCantidad() != null && d.getPrecioParaTicket() != null) {
                    totalVisible = totalVisible.add(
                            d.getPrecioParaTicket().multiply(BigDecimal.valueOf(d.getCantidad())));
                }
            }
        }
        m.put("totalTicket", totalVisible.setScale(2, RoundingMode.HALF_UP));
        var est = t.getEstado();
        String estadoLabel = (est == Ticket.Estado.PTE_PAGO) ? "NOTA DE ENCARGO"
                : (est == Ticket.Estado.PAGADO) ? "PAGADO" : "";

        List<String> copias = new ArrayList<>();
        copias.add("CLIENTE");

        // Si está pendiente de pago, añadimos la copia del operario
        if (est == Ticket.Estado.PTE_PAGO)
            copias.add("OPERARIO");

        m.put("estadoLabel", estadoLabel);
        m.put("copias", copias);

        m.put("ivaPct", t.getIva());
        m.put("comentarios", t.getComentarios());

        int numLineas = (t.getDetalles() != null) ? t.getDetalles().size() : 0;
        int pageHeight = 185 + (numLineas * 8);
        m.put("pageHeight", pageHeight);
        m.put("generadoAt", java.time.LocalDateTime.now());

        // Render de HTML
        String html = templateRenderService.render("tickets/ticket_pdf", m, locale);
        // HTML -> PDF
        return pdfService.htmlToPdf(html);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getHistoryByCliente(Long clienteId) {
        return ticketRepo.findHistoryByCliente(clienteId, PageRequest.of(0, 3));
    }

}
