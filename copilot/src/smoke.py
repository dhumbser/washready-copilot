"""Prueba de humo: valida que el cliente Python puede hablar con la API de
Wash & Ready de extremo a extremo (login JWT + lecturas reales).

Uso (desde copilot/, con el backend wash-ready arrancado):
    python -m src.smoke
"""

from __future__ import annotations

from datetime import date

from src.washready_client import WashReadyClient


def main() -> None:
    client = WashReadyClient()

    print("== Centros ==")
    for centro in client.listar_centros():
        print(f"  #{centro['id']}: {centro['nombre']}")

    hoy = date.today()
    primer_dia_mes = hoy.replace(day=1)

    print("\n== Resumen del mes actual ==")
    resumen = client.resumen(desde=primer_dia_mes.isoformat(), hasta=hoy.isoformat())
    print(f"  Tickets: {resumen['totalTickets']}")
    print(f"  Base:    {resumen['totalBase']} EUR")
    print(f"  IVA:     {resumen['totalIva']} EUR")
    print(f"  Total:   {resumen['totalConIva']} EUR")

    print("\nSmoke test OK: la conexion con Wash & Ready funciona correctamente.")


if __name__ == "__main__":
    main()
