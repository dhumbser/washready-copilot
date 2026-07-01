"""Validador rule-based del SQL generado por el LLM + la tool que lo ejecuta.

Este validador es defensa en profundidad, NO la frontera de seguridad real:
la barrera de verdad es que `copilot_ro` solo tiene SELECT sobre las tablas
de negocio (y columnas concretas de `user`, sin password/disabled_from),
verificado en la practica (UPDATE/DELETE y tablas fuera del whitelist
fallan por permisos aunque este validador tuviera un fallo).
"""

from __future__ import annotations

import re

import sqlparse
from langchain_core.tools import tool

from src.db import ejecutar_select

_PALABRAS_PROHIBIDAS = re.compile(
    r"\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|GRANT|REVOKE|TRUNCATE|REPLACE|"
    r"CALL|EXECUTE|LOAD_FILE|INTO\s+OUTFILE|INTO\s+DUMPFILE)\b",
    re.IGNORECASE,
)

_LIMIT_POR_DEFECTO = 200


class SqlInvalidoError(Exception):
    pass


def validar_sql(sql: str) -> str:
    """Valida una consulta de solo lectura y le fuerza un LIMIT si no lo trae.
    Lanza SqlInvalidoError si no la considera segura. Devuelve el SQL final.
    """
    sql = sql.strip().rstrip(";")
    if not sql:
        raise SqlInvalidoError("SQL vacio")

    sentencias = [s for s in sqlparse.parse(sql) if s.tokens]
    if len(sentencias) != 1:
        raise SqlInvalidoError("Solo se permite una unica sentencia SQL")

    primer_token = sentencias[0].token_first(skip_cm=True)
    if primer_token is None:
        raise SqlInvalidoError("SQL vacio")
    primera_palabra = primer_token.value.upper()
    if primera_palabra not in ("SELECT", "WITH"):
        raise SqlInvalidoError(
            f"Solo se permiten sentencias SELECT/WITH, no '{primera_palabra}'"
        )

    if _PALABRAS_PROHIBIDAS.search(sql):
        raise SqlInvalidoError("La sentencia contiene una palabra clave no permitida")

    if not re.search(r"\bLIMIT\s+\d+", sql, re.IGNORECASE):
        sql = f"{sql} LIMIT {_LIMIT_POR_DEFECTO}"

    return sql


@tool
def consultar_sql(sql: str) -> dict:
    """Ejecuta una consulta SQL de solo lectura (SELECT) contra la base de datos
    de Wash & Ready para resolver agregaciones, desgloses o conteos que las
    demas tools no cubren (por ejemplo: ventas por servicio, por operario, o
    conteos sobre una tabla completa como el total de clientes o anulaciones).
    Usa el esquema de tablas indicado en las instrucciones del sistema. Solo
    se admite una sentencia SELECT/WITH; cualquier intento de modificar datos
    se rechaza.
    """
    try:
        sql_validado = validar_sql(sql)
    except SqlInvalidoError as e:
        return {"error": f"SQL rechazado: {e}"}

    try:
        filas = ejecutar_select(sql_validado)
    except Exception as e:
        return {"error": str(e)}

    return {"filas": filas}
