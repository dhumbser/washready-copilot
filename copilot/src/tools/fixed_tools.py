"""Tools fijas del agente: envuelven la API de Wash & Ready para consultas de
negocio (facturacion, beneficio neto, busqueda de tickets, catalogo).

Enums (verificado el Dia 1 contra el backend real): los parametros de estado
y metodo de pago SOLO aceptan el name() en mayusculas (p.ej. "PAGADO",
"EFECTIVO"); la etiqueta en espanol ("pagado", "efectivo") devuelve 400.
Por eso se tipan como Literal con los valores exactos, para que el LLM no
pueda pasar otra cosa.
"""

from __future__ import annotations

from typing import Literal, Optional

from langchain_core.tools import tool

from src.washready_client import WashReadyClient

_client = WashReadyClient()  # login es lazy: no hay red hasta la 1a llamada

Estado = Literal["PTE_PAGO", "PAGADO", "CERRADO", "ANULADO"]
MetodoPago = Literal["TARJETA", "EFECTIVO", "BIZUM", "BONO", "TRANSFERENCIA", "OTRO"]


def _safe(fn):
    """Ejecuta la llamada al cliente devolviendo {"error": ...} en vez de lanzar,
    para que el LLM pueda ver y explicar el fallo en vez de romper el turno.
    """
    try:
        return fn()
    except Exception as e:
        return {"error": str(e)}


@tool
def resumen_facturacion(
    desde: str,
    hasta: str,
    centro_id: Optional[int] = None,
    estado: Optional[Estado] = None,
    metodo: Optional[MetodoPago] = None,
) -> dict:
    """Totales de facturacion (numero de tickets, base, IVA, total con IVA) en un
    rango de fechas. Por defecto solo cuenta tickets en estado PAGADO o CERRADO
    (ventas reales); pasa 'estado' si se pregunta explicitamente por otro estado
    (p.ej. pendientes de pago o anulados). Fechas en formato YYYY-MM-DD. Filtra
    opcionalmente por centro_id y/o metodo de pago.
    """
    return _safe(lambda: _client.resumen(
        desde=desde, hasta=hasta, centro_id=centro_id, estado=estado, metodo=metodo,
    ))


@tool
def beneficio_neto(desde: str, hasta: str, centro_id: Optional[int] = None) -> dict:
    """Beneficio neto en un rango de fechas: facturacion (tickets PAGADO/CERRADO)
    menos los adelantos de nomina aceptados en ese mismo periodo. Fechas en
    formato YYYY-MM-DD.
    """
    return _safe(lambda: _client.resumen_con_adelantos(
        desde=desde, hasta=hasta, centro_id=centro_id,
    ))


@tool
def facturacion_por_centro(periodo: Literal["dia", "mes"]) -> list[dict]:
    """Facturacion (tickets PAGADO/CERRADO) agrupada por centro, solo para 'hoy'
    (periodo='dia') o lo que llevamos del mes actual (periodo='mes'). No admite
    periodos historicos arbitrarios: para una comparativa por centro de un mes
    pasado no hay tool disponible.
    """
    return _safe(lambda: _client.facturacion_por_centro(periodo=periodo))


@tool
def buscar_tickets(
    matricula: Optional[str] = None,
    cliente: Optional[str] = None,
    operario: Optional[str] = None,
    referencia: Optional[str] = None,
    estado: Optional[Estado] = None,
    metodo_pago: Optional[MetodoPago] = None,
    fdesde: Optional[str] = None,
    fhasta: Optional[str] = None,
    centro_id: Optional[int] = None,
) -> dict:
    """Busca tickets por matricula, cliente, operario, referencia, estado, metodo
    de pago, rango de fechas (YYYY-MM-DD) y/o centro. A diferencia de
    resumen_facturacion, esta tool NO excluye ningun estado por defecto: si no
    se indica 'estado', devuelve tickets de cualquier estado, incluidos los
    anulados. Devuelve el numero total de coincidencias y como maximo las
    primeras 8 filas.
    """
    resultado = _safe(lambda: _client.buscar_tickets(
        matricula=matricula, cliente=cliente, operario=operario, referencia=referencia,
        estado=estado, metodo_pago=metodo_pago, fdesde=fdesde, fhasta=fhasta,
        centro_id=centro_id, size=8,
    ))
    if isinstance(resultado, dict) and "error" in resultado:
        return resultado
    return {
        "totalElements": resultado.get("totalElements"),
        "tickets": resultado.get("content", []),
    }


@tool
def listar_centros() -> list[dict]:
    """Lista todos los centros de trabajo (id y nombre)."""
    return _safe(lambda: _client.listar_centros())


@tool
def listar_servicios(centro_id: Optional[int] = None) -> list[dict]:
    """Lista el catalogo de servicios (id, descripcion, importe). Si se indica
    centro_id, solo los servicios disponibles en ese centro.
    """
    return _safe(lambda: _client.listar_servicios(centro_id=centro_id))
