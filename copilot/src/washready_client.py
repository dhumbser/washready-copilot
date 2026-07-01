"""Cliente HTTP de solo lectura para la API de Wash & Ready.

Login JWT con re-autenticacion automatica ante un 401 (el access token
expira a los 45 min, ver jwt.access-token-ttl en el backend). Los metodos
publicos devuelven el JSON crudo de cada endpoint; el formateo para el
agente se hace en capas posteriores (tools, Dia 2).

NOTA sobre enums en query params (verificado contra el backend real,
Dia 1): Ticket.Estado y Ticket.MetodoPago solo aceptan el NOMBRE del
enum en mayusculas (p.ej. "PAGADO", "EFECTIVO", "PTE_PAGO"). La etiqueta
en espanol ("pagado", "efectivo", "pte. de pago") devuelve 400 Bad
Request. Es el mismo valor que se persiste en BD, ya que
@Enumerated(EnumType.STRING) guarda name(). Pasar siempre el name().
"""

from __future__ import annotations

from typing import Any

import requests

from src.config import settings


class WashReadyClient:
    def __init__(
        self,
        base_url: str | None = None,
        usuario: str | None = None,
        password: str | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.base_url = (base_url or settings.washready_api_url).rstrip("/")
        self.usuario = usuario or settings.washready_api_user
        self.password = password or settings.washready_api_password
        self.timeout = timeout
        self._session = requests.Session()
        self._access_token: str | None = None

    # ------------------------------------------------------------------
    # Autenticacion
    # ------------------------------------------------------------------
    def _login(self) -> None:
        resp = self._session.post(
            f"{self.base_url}/auth/login",
            data={"usuario": self.usuario, "password": self.password},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        self._access_token = resp.json()["access_token"]

    def _request(self, method: str, path: str, *, params: dict[str, Any] | None = None,
                 _retry: bool = True) -> Any:
        if self._access_token is None:
            self._login()

        headers = {"Authorization": f"Bearer {self._access_token}"}
        resp = self._session.request(
            method, f"{self.base_url}{path}", params=params, headers=headers, timeout=self.timeout,
        )

        if resp.status_code == 401 and _retry:
            self._access_token = None
            return self._request(method, path, params=params, _retry=False)

        resp.raise_for_status()
        return resp.json() if resp.content else None

    @staticmethod
    def _clean(params: dict[str, Any]) -> dict[str, Any]:
        """Descarta claves con valor None para no mandarlas como query params."""
        return {k: v for k, v in params.items() if v is not None}

    def _get(self, path: str, **params: Any) -> Any:
        return self._request("GET", path, params=self._clean(params))

    # ------------------------------------------------------------------
    # Informes
    # ------------------------------------------------------------------
    def resumen(
        self,
        desde: str | None = None,
        hasta: str | None = None,
        centro_id: int | None = None,
        estado: str | None = None,
        metodo: str | None = None,
    ) -> dict[str, Any]:
        """GET /api/informes/resumen. desde/hasta en formato YYYY-MM-DD."""
        return self._get(
            "/api/informes/resumen",
            desde=desde, hasta=hasta, centroId=centro_id, estado=estado, metodoPago=metodo,
        )

    def resumen_con_adelantos(
        self,
        desde: str | None = None,
        hasta: str | None = None,
        centro_id: int | None = None,
        estado: str | None = None,
        metodo: str | None = None,
    ) -> dict[str, Any]:
        """GET /api/informes/resumen-con-adelantos (añade adelantosAceptados y beneficioNeto)."""
        return self._get(
            "/api/informes/resumen-con-adelantos",
            desde=desde, hasta=hasta, centroId=centro_id, estado=estado, metodoPago=metodo,
        )

    # ------------------------------------------------------------------
    # Dashboard admin
    # ------------------------------------------------------------------
    def facturacion_por_centro(self, periodo: str = "dia", centro_id: int | None = None) -> list[dict[str, Any]]:
        """GET /api/admin/dashboard/facturacion-{dia|mes}. Requiere rol ADMIN."""
        if periodo not in ("dia", "mes"):
            raise ValueError("periodo debe ser 'dia' o 'mes'")
        return self._get(f"/api/admin/dashboard/facturacion-{periodo}", centroId=centro_id)

    # ------------------------------------------------------------------
    # Tickets
    # ------------------------------------------------------------------
    def buscar_tickets(
        self,
        matricula: str | None = None,
        marca: str | None = None,
        modelo: str | None = None,
        color: str | None = None,
        cliente: str | None = None,
        telefono: str | None = None,
        operario: str | None = None,
        referencia: str | None = None,
        estado: str | None = None,
        fdesde: str | None = None,
        fhasta: str | None = None,
        centro_id: int | None = None,
        metodo_pago: str | None = None,
        page: int = 0,
        size: int = 15,
    ) -> dict[str, Any]:
        """GET /api/tickets/page. fdesde/fhasta en formato YYYY-MM-DD."""
        return self._get(
            "/api/tickets/page",
            matricula=matricula, marca=marca, modelo=modelo, color=color,
            cliente=cliente, telefono=telefono, operario=operario, referencia=referencia,
            estado=estado, fdesde=fdesde, fhasta=fhasta,
            centroId=centro_id, metodoPago=metodo_pago,
            page=page, size=size,
        )

    # ------------------------------------------------------------------
    # Catalogo
    # ------------------------------------------------------------------
    def listar_centros(self) -> list[dict[str, Any]]:
        """GET /api/centros."""
        return self._get("/api/centros")

    def listar_servicios(self, centro_id: int | None = None) -> list[dict[str, Any]]:
        """GET /api/servicios. Sin centro_id devuelve el catalogo completo."""
        return self._get("/api/servicios", centroId=centro_id)
