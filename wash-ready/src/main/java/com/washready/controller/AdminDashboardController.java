package com.washready.controller;

import com.washready.repository.proyecciones.CentroFacturacionAgg;
import com.washready.service.AdminDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

  private final AdminDashboardService svc;
  public AdminDashboardController(AdminDashboardService svc) { this.svc = svc; }

  private boolean isAdmin(Jwt jwt) {
    return jwt != null && "ROLE_ADMIN".equals(jwt.getClaimAsString("role"));
  }

  @GetMapping("/facturacion-dia")
  public List<CentroFacturacionAgg> facturacionDia(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) Long centroId) {
    if (!isAdmin(jwt)) return List.of();
    return svc.facturacionDiaPorCentro(centroId);
  }

  @GetMapping("/facturacion-mes")
  public List<CentroFacturacionAgg> facturacionMes(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) Long centroId) {
    if (!isAdmin(jwt)) return List.of();
    return svc.facturacionMesPorCentro(centroId);
  }
}
