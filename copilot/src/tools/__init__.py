"""Paquete de tools del agente. TOOLS es la lista que se pasa a bind_tools()."""

from src.tools.fixed_tools import (
    beneficio_neto,
    buscar_tickets,
    facturacion_por_centro,
    listar_centros,
    listar_servicios,
    resumen_facturacion,
)

TOOLS = [
    resumen_facturacion,
    beneficio_neto,
    facturacion_por_centro,
    buscar_tickets,
    listar_centros,
    listar_servicios,
]
