"""Motor de solo lectura para el escape a text-to-SQL (usuario copilot_ro).

La conexion se abre de forma lazy y se cachea. Requiere WASHREADY_DB_URL en
el .env (ver .env.example); si no esta configurada, este modulo simplemente
no se usa (src/tools/__init__.py solo registra consultar_sql cuando hay URL).

La frontera de seguridad real es el propio usuario `copilot_ro` (SELECT de
solo lectura sobre las tablas de negocio, con columnas restringidas en
`user`), verificado en la practica: no puede escribir ni leer fuera
del whitelist aunque el SQL que le llegue no estuviera bien validado.
"""

from __future__ import annotations

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from src.config import settings

_engine: Engine | None = None


def _get_engine() -> Engine:
    global _engine
    if _engine is None:
        _engine = create_engine(settings.washready_db_url, pool_pre_ping=True)
    return _engine


def ejecutar_select(sql: str, timeout_seconds: int = 10) -> list[dict]:
    """Ejecuta una SELECT ya validada y devuelve las filas como lista de dicts.

    MAX_EXECUTION_TIME (MySQL 8+) corta la consulta si se pasa del timeout;
    solo aplica a SELECT, que es exactamente nuestro caso de uso.
    """
    engine = _get_engine()
    with engine.connect() as conn:
        conn.execute(text(f"SET SESSION MAX_EXECUTION_TIME={timeout_seconds * 1000}"))
        result = conn.execute(text(sql))
        columnas = list(result.keys())
        return [dict(zip(columnas, fila)) for fila in result.fetchall()]
