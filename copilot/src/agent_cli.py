"""Runner de linea de comandos del agente.

Uso (desde copilot/, con el backend wash-ready arrancado):
    python -m src.agent_cli "¿cuánto facturamos en junio?"
"""

from __future__ import annotations

import sys

from src.agent import run


def main() -> None:
    if len(sys.argv) < 2:
        print('Uso: python -m src.agent_cli "<pregunta>"')
        raise SystemExit(1)

    pregunta = sys.argv[1]
    print(run(pregunta))


if __name__ == "__main__":
    main()
