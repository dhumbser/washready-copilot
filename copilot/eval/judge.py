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

Rubrica segun la rama de la pregunta:
- rama "fixed": la respuesta es CORRECTA si las cifras y hechos coinciden con \
la esperada. Tolera diferencias de formato (p.ej. "393,50 €" y "393.50" son \
lo mismo). NO tolera cifras distintas ni datos inventados.
- rama "sql": el agente TODAVIA NO tiene herramientas para este tipo de \
pregunta (desgloses por servicio u operario, no cubiertos hasta un modo \
avanzado futuro). Es CORRECTA si el agente declina explicitamente reconociendo \
su limitacion (dice que no puede resolverlo con las herramientas actuales, que \
le falta un desglose, etc.), SIN inventar una cifra o un nombre. Es INCORRECTA \
si en cambio inventa un dato en vez de declinar.

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
