package com.washready.controller;

import com.washready.model.Ticket;
import com.washready.model.Ticket.Estado;
import com.washready.repository.TicketRepository;
import com.washready.repository.proyecciones.*;
import com.washready.service.InformesService;
import com.washready.service.InformesService.ResumenConAdelantos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.washready.util.FechasUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@RestController
@RequestMapping("/api/informes")
public class InformesController {

    private final InformesService svc;
    private final TicketRepository ticketRepo;
    public InformesController(InformesService svc, TicketRepository ticketRepo) {
        this.svc = svc;
        this.ticketRepo = ticketRepo;
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
    }

    private Long resolveCentroId(Jwt jwt, Long centroIdParam) {
        if (isAdmin(jwt))
            return centroIdParam;
        Object c = (jwt != null) ? jwt.getClaim("centroId") : null;
        return (c instanceof Number) ? ((Number) c).longValue() : null;
    }

    private LocalDateTime startOf(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private LocalDateTime nextDay(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay();
    }

    // -------- Resumen --------
    @GetMapping("/resumen")
    public ResumenInforme resumen(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long centroId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false, name = "metodoPago") Ticket.MetodoPago metodoPago,
            @RequestParam(required = false, name = "metodo") Ticket.MetodoPago metodoAlias) {
        Long c = resolveCentroId(jwt, centroId);

        LocalDateTime d = startOf(desde);
        LocalDateTime h = nextDay(hasta);

        Ticket.MetodoPago metodo = (metodoPago != null) ? metodoPago : metodoAlias;

        return svc.resumen(c, d, h, estado, metodo);
    }

    // -------- LISTA DE TICKETS para informes --------
    @GetMapping("/tickets")
    public Page<TicketInformeRow> informeTickets(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, name = "fdesde") String fdesde,
            @RequestParam(required = false, name = "fhasta") String fhasta,
            @RequestParam(required = false) Long centroId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10000") int size // grande para sumar KPIs en el front
    ) {
        boolean admin = (jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role")));
        Long centroFiltro = admin ? centroId : resolveCentroId(jwt, null);

        Instant desde = null, hastaLdt = null;
        try {
            if (fdesde != null && !fdesde.isBlank()) {
                desde = LocalDate.parse(fdesde).atStartOfDay(FechasUtil.ZONE).toInstant();
            }
        } catch (Exception ignored) {
        }
        try {
            if (fhasta != null && !fhasta.isBlank()) {
                hastaLdt = LocalDate.parse(fhasta).atTime(23, 59, 59).atZone(FechasUtil.ZONE).toInstant();
            }
        } catch (Exception ignored) {
        }

        var pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(size, 1), 10000),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));

        return ticketRepo.informeTickets(centroFiltro, desde, hastaLdt, pageable);
    }

    @GetMapping("/resumen-con-adelantos")
    public ResumenConAdelantos resumenConAdelantos(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long centroId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false, name = "metodoPago") Ticket.MetodoPago metodoPago,
            @RequestParam(required = false, name = "metodo") Ticket.MetodoPago metodoAlias) {
        Long c = resolveCentroId(jwt, centroId);
        LocalDateTime d = startOf(desde);
        LocalDateTime h = nextDay(hasta);
        Ticket.MetodoPago metodo = (metodoPago != null) ? metodoPago : metodoAlias;

        return svc.resumenConAdelantos(c, d, h, estado, metodo);
    }

    @GetMapping(value = "/cierre/print")
    public ResponseEntity<byte[]> cierreDiarioPdf(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long centroId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false, name = "metodoPago") Ticket.MetodoPago metodoPago,
            @RequestParam(required = false, name = "metodo") Ticket.MetodoPago metodoAlias,
            Locale locale) {
        if (desde == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El parámetro 'desde' es obligatorio");
        }

        LocalDate hastaVal = (hasta != null) ? hasta : desde;
        Long c = resolveCentroId(jwt, centroId);

        LocalDateTime inicio = startOf(desde);
        LocalDateTime fin = nextDay(hastaVal);

        boolean admin = isAdmin(jwt);
        Ticket.MetodoPago metodo = (metodoPago != null) ? metodoPago : metodoAlias;
        byte[] pdf = svc.generarCierreDiarioPdf(c, inicio, fin, metodo, admin, locale);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String fileName = String.format("cierre-%s.pdf", desde);
        headers.setContentDisposition(ContentDisposition.inline().filename(fileName).build());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

}
