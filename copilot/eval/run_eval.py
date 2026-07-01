"""Harness de evaluacion: corre las preguntas de golden.yaml contra el agente,
compara cada respuesta con un LLM-as-judge (eval/judge.py) y persiste el
veredicto como score en la traza de Langfuse correspondiente.

La traza de cada pregunta se etiqueta con `golden-<id>` (via run_with_trace)
y se recupera despues consultando por ese tag: en este SDK (Langfuse 4.12,
OTel) el trace_id lo asigna el propio exportador y no se puede fijar de
antemano (verificado contra la API real), asi que tags es el mecanismo de
correlacion que si funciona.

Uso (desde copilot/, con el backend wash-ready arrancado):
    python -m eval.run_eval
"""

from __future__ import annotations

import time
from pathlib import Path

import yaml

from eval.judge import evaluar
from src.agent import run_with_trace
from src.observability import get_langfuse_handler

GOLDEN_PATH = Path(__file__).parent / "golden.yaml"
REPORT_PATH = Path(__file__).parent / "report.md"


def _cargar_golden() -> list[dict]:
    return yaml.safe_load(GOLDEN_PATH.read_text(encoding="utf-8"))


def _buscar_trace_id_por_tag(tag: str, intentos: int = 6, espera: float = 2.0) -> str | None:
    """La ingesta de Langfuse es asincrona: reintenta unos segundos antes de
    rendirse. Si no aparece, la puntuacion de esa pregunta simplemente no se
    persiste en Langfuse (el resultado del juez sigue yendo al report.md).
    """
    from langfuse import get_client

    client = get_client()
    for _ in range(intentos):
        traces = client.api.trace.list(tags=[tag], limit=1)
        if traces.data:
            return traces.data[0].id
        time.sleep(espera)
    return None


def _persistir_score(trace_id: str, correcto: bool, comentario: str) -> None:
    from langfuse import get_client

    get_client().create_score(
        trace_id=trace_id,
        name="golden_eval",
        value=1 if correcto else 0,
        data_type="BOOLEAN",
        comment=comentario,
    )


def _escribir_reporte(resultados: list[dict]) -> None:
    total = len(resultados)
    correctos = sum(1 for r in resultados if r["correcto"])

    por_rama: dict[str, list[dict]] = {}
    for r in resultados:
        por_rama.setdefault(r["rama"], []).append(r)

    lineas = ["# Reporte de evaluacion — washready-copilot", ""]
    lineas.append(f"**Accuracy global: {correctos}/{total} ({correctos / total:.0%})**")
    lineas.append("")
    for rama in sorted(por_rama):
        items = por_rama[rama]
        ok = sum(1 for r in items if r["correcto"])
        lineas.append(f"- Rama `{rama}`: {ok}/{len(items)} ({ok / len(items):.0%})")

    lineas.append("")
    lineas.append("| id | rama | resultado | comentario del juez |")
    lineas.append("|---|---|---|---|")
    for r in resultados:
        estado = "correcto" if r["correcto"] else "**FALLO**"
        comentario = r["comentario"].replace("|", "/").replace("\n", " ")
        lineas.append(f"| {r['id']} | {r['rama']} | {estado} | {comentario} |")

    fallos = [r for r in resultados if not r["correcto"]]
    if fallos:
        lineas.append("")
        lineas.append("## Fallos en detalle")
        for r in fallos:
            lineas.append(f"\n### {r['id']} — {r['pregunta']}")
            lineas.append(f"- **Esperada:** {r['esperada']}")
            lineas.append(f"- **Obtenida:** {r['respuesta']}")
            lineas.append(f"- **Juez:** {r['comentario']}")

    REPORT_PATH.write_text("\n".join(lineas) + "\n", encoding="utf-8")


def main() -> None:
    preguntas = _cargar_golden()
    langfuse_activo = get_langfuse_handler() is not None

    resultados = []
    for item in preguntas:
        qid = item["id"]
        tag = f"golden-{qid}"

        respuesta, _traza = run_with_trace(item["pregunta"], thread_id=f"eval-{qid}", tags=[tag])
        veredicto = evaluar(
            item["pregunta"], item["respuesta_esperada"], respuesta, item["rama_esperada"]
        )

        if langfuse_activo:
            from langfuse import get_client

            get_client().flush()
            trace_id = _buscar_trace_id_por_tag(tag)
            if trace_id:
                _persistir_score(trace_id, veredicto.correcto, veredicto.comentario)

        resultados.append(
            {
                "id": qid,
                "rama": item["rama_esperada"],
                "pregunta": item["pregunta"],
                "esperada": item["respuesta_esperada"],
                "respuesta": respuesta,
                "correcto": veredicto.correcto,
                "comentario": veredicto.comentario,
            }
        )
        estado = "OK" if veredicto.correcto else "FALLO"
        print(f"[{estado}] {qid} ({item['rama_esperada']})")

    _escribir_reporte(resultados)

    correctos = sum(1 for r in resultados if r["correcto"])
    print(f"\nAccuracy global: {correctos}/{len(resultados)}")
    print(f"Reporte escrito en {REPORT_PATH}")


if __name__ == "__main__":
    main()
