"""Configuracion del agente, cargada desde variables de entorno.

El .env vive en la raiz del monorepo (washready-copilot/.env), no dentro
de copilot/, porque tambien lo usa el backend wash-ready/. Nunca se versiona.
"""

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

_ROOT_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=_ROOT_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",  # el .env tambien trae variables propias de wash-ready/
    )

    # ---- Wash & Ready API (necesario desde el Dia 1) ----
    washready_api_url: str = "http://localhost:8080"
    washready_api_user: str
    washready_api_password: str

    # ---- LLM (Dia 2+) ----
    openai_api_key: str | None = None
    copilot_model: str = "gpt-5-mini"

    # ---- Acceso SQL de solo lectura para el escape text-to-SQL (Dia 5) ----
    washready_db_url: str | None = None

    # ---- Observabilidad con Langfuse (Dia 4) ----
    langfuse_base_url: str | None = None
    langfuse_public_key: str | None = None
    langfuse_secret_key: str | None = None


settings = Settings()
