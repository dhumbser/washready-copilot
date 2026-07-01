"""Catalogo de centros y servicios de Wash & Ready, cacheado en memoria para
inyectarse en el system prompt del agente (permite mapear "centro Aeropuerto"
a su centro_id y conocer los nombres de servicio sin tener que listarlos con
una tool en cada turno).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from threading import Lock

from src.washready_client import WashReadyClient


@dataclass
class Catalogo:
    centros: list[dict] = field(default_factory=list)
    servicios: list[dict] = field(default_factory=list)

    def centro_id_por_nombre(self, nombre: str) -> int | None:
        """Busca un centro por nombre exacto (sin distinguir mayusculas/acentos simples)."""
        objetivo = nombre.strip().casefold()
        for c in self.centros:
            if (c.get("nombre") or "").strip().casefold() == objetivo:
                return c.get("id")
        return None

    def prompt_block(self) -> str:
        """Bloque de texto listo para inyectar en el system prompt del agente."""
        centros_txt = "\n".join(f"  - id={c['id']}: {c['nombre']}" for c in self.centros)
        servicios_txt = "\n".join(f"  - id={s['id']}: {s['descripcion']}" for s in self.servicios)
        return (
            "Centros de trabajo disponibles (usa el id al llamar a las tools):\n"
            f"{centros_txt}\n\n"
            f"Catalogo de servicios ({len(self.servicios)} en total):\n"
            f"{servicios_txt}"
        )


_lock = Lock()
_catalogo: Catalogo | None = None


def get_catalogo(client: WashReadyClient | None = None) -> Catalogo:
    """Carga el catalogo (centros + servicios) una sola vez y lo cachea en memoria
    para el resto del proceso. Llamadas posteriores no vuelven a golpear la API.
    """
    global _catalogo
    with _lock:
        if _catalogo is None:
            c = client or WashReadyClient()
            _catalogo = Catalogo(centros=c.listar_centros(), servicios=c.listar_servicios())
    return _catalogo
