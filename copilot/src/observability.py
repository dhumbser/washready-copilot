"""Instrumentacion con Langfuse. Handler cacheado con degradacion elegante:
si no hay claves configuradas (p.ej. clonaste el repo sin levantar el stack
de Langfuse), get_langfuse_handler() devuelve None y el agente sigue
funcionando exactamente igual, solo que sin trazas.

Las credenciales se pasan explicitas al constructor de Langfuse en vez de
confiar en os.environ: pydantic-settings lee nuestro .env hacia su propio
modelo (settings), pero NO vuelca esas variables al entorno del proceso.
"""

from __future__ import annotations

from langfuse import Langfuse
from langfuse.langchain import CallbackHandler

from src.config import settings

_handler: CallbackHandler | None = None
_initialized = False


def get_langfuse_handler() -> CallbackHandler | None:
    global _handler, _initialized
    if _initialized:
        return _handler
    _initialized = True

    if not (settings.langfuse_public_key and settings.langfuse_secret_key):
        return None

    Langfuse(
        public_key=settings.langfuse_public_key,
        secret_key=settings.langfuse_secret_key,
        base_url=settings.langfuse_base_url,
    )
    _handler = CallbackHandler()
    return _handler
