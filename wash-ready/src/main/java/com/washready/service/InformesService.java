// InformesService.java
package com.washready.service;

import com.washready.model.CentroTrabajo;
import com.washready.model.Ticket;
import com.washready.model.Ticket.Estado;
import com.washready.pdf.PdfService;
import com.washready.pdf.TemplateRenderService;
import com.washready.repository.TicketRepository;
import com.washready.repository.AdelantoRepository;
import com.washready.repository.CentroTrabajoRepository;
import com.washready.repository.proyecciones.*;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import com.washready.util.FechasUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class InformesService {

  private final TicketRepository tickets;
  private final AdelantoRepository adelantos;
  private final CentroTrabajoRepository centros;
  private final TemplateRenderService templateRenderer;
  private final PdfService pdfService;
  private static final List<Ticket.MetodoPago> METODO_DISPLAY_ORDER = List.of(
      Ticket.MetodoPago.EFECTIVO,
      Ticket.MetodoPago.TARJETA,
      Ticket.MetodoPago.BIZUM,
      Ticket.MetodoPago.BONO,
      Ticket.MetodoPago.TRANSFERENCIA,
      Ticket.MetodoPago.OTRO);
  public InformesService(TicketRepository tickets,
      AdelantoRepository adelantos,
      CentroTrabajoRepository centros,
      TemplateRenderService templateRenderer,
      PdfService pdfService) {
    this.tickets = tickets;
    this.adelantos = adelantos;
    this.centros = centros;
    this.templateRenderer = templateRenderer;
    this.pdfService = pdfService;
  }

  /**
   * DTO de salida: añade adelantos aceptados y “beneficioNeto” al resumen base.
   */
  public record ResumenConAdelantos(
      ResumenInforme base,
      BigDecimal adelantosAceptados,
      BigDecimal beneficioNeto) {
  }

  // === API original (si alguien lo usa, lo dejamos intacto) ===
  public ResumenInforme resumen(Long centroId,
      LocalDateTime desde,
      LocalDateTime hastaExcl,
      Estado estado,
      Ticket.MetodoPago metodo) {
    return tickets.resumenGlobal(
        centroId,
        FechasUtil.toInstant(desde),
        FechasUtil.toInstant(hastaExcl),
        estado,
        metodo);
  }

  public ResumenInforme resumen(Long centroId, LocalDateTime desde, LocalDateTime hastaExcl) {
    return resumen(centroId, desde, hastaExcl, null, null);
  }

  /** NUEVO: mismo resumen + adelantos aceptados y neto (base - adelantos). */
  public ResumenConAdelantos resumenConAdelantos(Long centroId,
      LocalDateTime desde,
      LocalDateTime hastaExcl,
      Estado estado,
      Ticket.MetodoPago metodo) {
    var base = tickets.resumenGlobal(
        centroId,
        FechasUtil.toInstant(desde),
        FechasUtil.toInstant(hastaExcl),
        estado,
        metodo);

    // Ajusta aquí si tu proyección usa otro nombre para el “ingreso/total”.
    BigDecimal ingresos = nz(base.getTotalConIva());
    BigDecimal adel = nz(adelantos.sumAceptados(centroId, desde, hastaExcl));
    BigDecimal neto = ingresos.subtract(adel);

    return new ResumenConAdelantos(base, adel, neto);
  }

  public ResumenConAdelantos resumenConAdelantos(Long centroId,
      LocalDateTime desde,
      LocalDateTime hastaExcl) {
    return resumenConAdelantos(centroId, desde, hastaExcl, null, null);
  }

  public byte[] generarCierreDiarioPdf(Long centroId, LocalDateTime desde, LocalDateTime hastaExcl,
      Ticket.MetodoPago metodo, boolean isAdmin, Locale locale) {
    LocalDateTime inicio = (desde != null) ? desde : LocalDate.now(FechasUtil.ZONE).atStartOfDay();
    LocalDateTime fin = (hastaExcl != null) ? hastaExcl : inicio.plusDays(1);
    if (!fin.isAfter(inicio)) {
      throw new IllegalArgumentException("El rango del informe debe tener al menos un día");
    }

    var rows = tickets.cierreDiaTickets(centroId, FechasUtil.toInstant(inicio), FechasUtil.toInstant(fin), metodo);
    List<TicketCierreLinea> lineas = rows.stream()
        .map(t -> new TicketCierreLinea(
            t.getFecha(),
            numeroTicket(t),
            textoONada(t.getCliente() != null ? t.getCliente().getTelefono() : null),
            textoONada(t.getVehiculo() != null ? t.getVehiculo().getMatricula() : null),
            textoONada(t.getCentro() != null ? t.getCentro().getNombre() : null),
            nz(t.getTotal())))
        .collect(Collectors.toList());

    BigDecimal totalImporte = lineas.stream()
        .map(TicketCierreLinea::importe)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    long totalTickets = lineas.size();

    BigDecimal adelantosAceptados = nz(adelantos.sumAceptados(centroId, inicio, fin));
    BigDecimal beneficioNeto = totalImporte.subtract(adelantosAceptados);
    List<MetodoPagoLinea> totalesMetodos = buildTotalesMetodos(centroId, inicio, fin, metodo);

    LocalDate fechaDesde = inicio.toLocalDate();
    LocalDate fechaHasta = fin.minusSeconds(1).toLocalDate();

    List<TicketCierreLinea> pendientes = List.of();
    BigDecimal totalPendientes = BigDecimal.ZERO;
    long totalTicketsPendientes = 0;
    if (isAdmin) {
      var pendRows = tickets.cierreDiaPendientes(centroId, FechasUtil.toInstant(inicio), FechasUtil.toInstant(fin));
      pendientes = pendRows.stream()
          .map(t -> new TicketCierreLinea(
              t.getFecha(),
              numeroTicket(t),
              textoONada(t.getCliente() != null ? t.getCliente().getTelefono() : null),
              textoONada(t.getVehiculo() != null ? t.getVehiculo().getMatricula() : null),
              textoONada(t.getCentro() != null ? t.getCentro().getNombre() : null),
              nz(t.getTotal())))
          .collect(Collectors.toList());
      totalPendientes = pendientes.stream()
          .map(TicketCierreLinea::importe)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      totalTicketsPendientes = pendientes.size();
    }

    Map<String, Object> model = new HashMap<>();
    model.put("centroNombre", resolveCentroNombre(centroId));
    model.put("fechaDesde", fechaDesde);
    model.put("fechaHasta", fechaHasta);
    model.put("mismoDia", fechaDesde.equals(fechaHasta));
    model.put("tickets", lineas);
    model.put("totalTickets", totalTickets);
    model.put("totalImporte", totalImporte);
    model.put("adelantosAceptados", adelantosAceptados);
    model.put("beneficioNeto", beneficioNeto);
    model.put("totalesMetodos", totalesMetodos);
    model.put("pendientes", pendientes);
    model.put("totalTicketsPendientes", totalTicketsPendientes);
    model.put("totalImportePendientes", totalPendientes);
    model.put("isAdmin", isAdmin);
    model.put("generadoAt", LocalDateTime.now());

    if (isAdmin) {
      TreeMap<LocalDate, List<TicketCierreLinea>> byDate = lineas.stream()
          .collect(Collectors.groupingBy(
              l -> l.fecha() != null ? l.fecha().atZone(FechasUtil.ZONE).toLocalDate() : LocalDate.MIN,
              TreeMap::new,
              Collectors.toList()));
      model.put("ticketsByDate", byDate);

      TreeMap<LocalDate, List<TicketCierreLinea>> pendientesByDate = pendientes.stream()
          .collect(Collectors.groupingBy(
              l -> l.fecha() != null ? l.fecha().atZone(FechasUtil.ZONE).toLocalDate() : LocalDate.MIN,
              TreeMap::new,
              Collectors.toList()));
      model.put("pendientesByDate", pendientesByDate);
    }

    if (!isAdmin) {
      int metodosBase = totalesMetodos.isEmpty() ? 0 : 16;
      int pageHeight = 90 + (lineas.size() * 5) + metodosBase + (totalesMetodos.size() * 4);
      model.put("pageHeight", Math.max(pageHeight, 150));
    }

    String html = templateRenderer.render("informes/informe_pdf", model, locale);
    return pdfService.htmlToPdf(html);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v != null ? v : BigDecimal.ZERO;
  }

  private String numeroTicket(Ticket ticket) {
    String ref = ticket.getReferencia();
    if (ref != null && !ref.isBlank())
      return ref;
    Long id = ticket.getId();
    return (id != null) ? String.valueOf(id) : "—";
  }

  private String textoONada(String value) {
    return (value == null || value.isBlank()) ? "—" : value;
  }

  private String resolveCentroNombre(Long centroId) {
    if (centroId == null)
      return "Todos los centros";
    return centros.findById(centroId)
        .map(CentroTrabajo::getNombre)
        .filter(n -> n != null && !n.isBlank())
        .orElse("Centro " + centroId);
  }

  private List<MetodoPagoLinea> buildTotalesMetodos(Long centroId,
      LocalDateTime inicio,
      LocalDateTime fin,
      Ticket.MetodoPago metodoFiltro) {
    Map<Ticket.MetodoPago, BigDecimal> acumulado = new EnumMap<>(Ticket.MetodoPago.class);
    for (MetodoPagoTotalRow row : tickets.totalesPorMetodoPago(centroId, FechasUtil.toInstant(inicio), FechasUtil.toInstant(fin),
        metodoFiltro)) {
      Ticket.MetodoPago metodo = row.getMetodoPago();
      BigDecimal total = nz(row.getTotal());
      if (total.signum() <= 0)
        continue;
      acumulado.merge(metodo, total, BigDecimal::add);
    }

    List<MetodoPagoLinea> lista = new ArrayList<>();
    if (metodoFiltro != null) {
      acumulado.forEach((metodo, total) -> {
        if (total != null && total.signum() > 0) {
          lista.add(new MetodoPagoLinea(formatearMetodo(metodo), total));
        }
      });
      return lista;
    }

    for (Ticket.MetodoPago metodo : METODO_DISPLAY_ORDER) {
      BigDecimal total = acumulado.remove(metodo);
      if (total != null && total.signum() > 0) {
        lista.add(new MetodoPagoLinea(formatearMetodo(metodo), total));
      }
    }

    acumulado.forEach((metodo, total) -> {
      if (total != null && total.signum() > 0) {
        lista.add(new MetodoPagoLinea(formatearMetodo(metodo), total));
      }
    });

    return lista;
  }

  private String formatearMetodo(Ticket.MetodoPago metodo) {
    if (metodo == null)
      return "Otro";
    String label = metodo.getLabel();
    if (label == null || label.isBlank()) {
      return metodo.name().charAt(0) + metodo.name().substring(1).toLowerCase();
    }
    return Character.toUpperCase(label.charAt(0)) + label.substring(1).toLowerCase();
  }

  public record TicketCierreLinea(Instant fecha, String numero, String telefono, String matricula, String centro,
      BigDecimal importe) {
  }

  public record MetodoPagoLinea(String etiqueta, BigDecimal total) {
  }

}
