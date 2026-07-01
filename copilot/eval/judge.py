"""LLM-as-judge del harness de evaluacion: compara la respuesta obtenida del
agente contra la respuesta esperada del golden dataset (mismo patron que
src/guardrails.py: salida estructurada con Pydantic).
"""

from __future__ import annotations

from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from src.config import settings


class EvalJudge(BaseModel):
    correcto: bool = Field(
        description="True si la respuesta obtenida es correcta segun la rubrica de su rama"
    )
    comentario: str = Field(description="Breve justificacion en espanol")


_JUEZ_PROMPT = """\
Eres un juez que evalua las respuestas de un agente de analitica de negocio \
de Wash & Ready, comparandolas con una respuesta esperada de referencia.

Rubrica (aplica por igual a las ramas "fixed" y "sql": el agente tiene un \
escape a text-to-SQL ademas de las tools fijas, asi que no basta con \
declinar en la rama sql; debe acertar el dato real igual que en la rama fixed):
- La respuesta es CORRECTA si las cifras y hechos coinciden con la esperada. \
Tolera diferencias de formato (p.ej. "393,50 €" y "393.50" son lo mismo, o \
listar datos adicionales que no contradicen la referencia).
- Es INCORRECTA si las cifras no coinciden, si inventa un dato, o si declina \
una pregunta que SI se puede resolver con las herramientas actuales.
- EXCEPCION importante: si la propia respuesta esperada de referencia indica \
que no hay datos para ese periodo/filtro (p.ej. "sin datos", "no hay tickets \
registrados"), entonces una respuesta que tambien indique ausencia de datos \
ES CORRECTA, aunque este formulada como "no tengo datos disponibles" o \
similar. Eso no es "declinar la pregunta": es la respuesta correcta.

Pregunta: {pregunta}
Rama: {rama}
Respuesta esperada (referencia): {esperada}
Respuesta obtenida del agente: {obtenida}
"""

_llm = ChatOpenAI(model=settings.copilot_model, api_key=settings.openai_api_key, temperature=0)
_juez = _llm.with_structured_output(EvalJudge)


def evaluar(pregunta: str, esperada: str, obtenida: str, rama: str) -> EvalJudge:
    return _juez.invoke(
        _JUEZ_PROMPT.format(pregunta=pregunta, rama=rama, esperada=esperada, obtenida=obtenida)
    )
