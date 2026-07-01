package com.washready.service;

import com.washready.repository.TicketRepository;
import com.washready.repository.proyecciones.CentroFacturacionAgg;
import com.washready.util.FechasUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminDashboardService {

    private final TicketRepository ticketRepo;
    public AdminDashboardService(TicketRepository ticketRepo) {
        this.ticketRepo = ticketRepo;
    }

    public List<CentroFacturacionAgg> facturacionDiaPorCentro(Long centroId) {
        LocalDateTime desde = inicioDiaActual();
        LocalDateTime hastaExcl = inicioDiaSiguiente();
        return ticketRepo.facturacionPorCentro(centroId, FechasUtil.toInstant(desde), FechasUtil.toInstant(hastaExcl));
    }

    public List<CentroFacturacionAgg> facturacionMesPorCentro(Long centroId) {
        LocalDateTime desde = inicioMesActual();
        LocalDateTime hastaExcl = inicioDiaSiguiente();
        return ticketRepo.facturacionPorCentro(centroId, FechasUtil.toInstant(desde), FechasUtil.toInstant(hastaExcl));
    }

    public static LocalDateTime inicioMesActual() {
        LocalDate hoy = LocalDate.now(FechasUtil.ZONE);
        return hoy.withDayOfMonth(1).atStartOfDay();
    }

    public static LocalDateTime inicioDiaActual() {
        LocalDate hoy = LocalDate.now(FechasUtil.ZONE);
        return hoy.atStartOfDay();
    }

    public static LocalDateTime inicioDiaSiguiente() {
        LocalDate hoy = LocalDate.now(FechasUtil.ZONE);
        return hoy.plusDays(1).atStartOfDay();
    }

}
