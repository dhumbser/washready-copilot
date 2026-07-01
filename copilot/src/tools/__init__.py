"""Paquete de tools del agente. TOOLS es la lista que se pasa a bind_tools()."""

from src.config import settings
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

# Escape a text-to-SQL: solo se registra si hay una conexion de solo lectura
# configurada (copilot_ro). Sin ella, el agente degrada al comportamiento del
# Dia 4 y declina las preguntas de rama sql en vez de fallar al importar.
if settings.washready_db_url:
    from src.tools.sql_tool import consultar_sql

    TOOLS.append(consultar_sql)
