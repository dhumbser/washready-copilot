"""Grafo del agente: guardrail de entrada + LLM/tools fijas, con memoria
conversacional por thread_id.

    START -> guardrail -> (bloqueado?) -> END
                        -> agent -> (tools_condition) -> tools -> agent -> ... -> END

El nodo `agent` esta aislado en su propia funcion para poder anteponer el
guardrail sin tocar su logica interna.
"""

from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, SystemMessage, ToolMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

from src.config import settings
from src.guardrails import evaluar_entrada, mensaje_rechazo
from src.observability import get_langfuse_handler
from src.prompts import build_system_prompt
from src.tools import TOOLS


class State(TypedDict):
    messages: Annotated[list, add_messages]
    bloqueado: bool


_llm = ChatOpenAI(model=settings.copilot_model, api_key=settings.openai_api_key, temperature=0)
_llm_con_tools = _llm.bind_tools(TOOLS)


def guardrail_node(state: State) -> dict:
    """Clasifica la ultima pregunta del usuario. Si no esta permitida, corta el
    turno aqui mismo con un mensaje de rechazo y no llega a llamar al LLM
    principal ni a ninguna tool.
    """
    pregunta = state["messages"][-1].content
    decision = evaluar_entrada(pregunta)
    if not decision.permitido:
        return {"messages": [AIMessage(content=mensaje_rechazo(decision))], "bloqueado": True}
    return {"bloqueado": False}


def _route_after_guardrail(state: State) -> str:
    return END if state.get("bloqueado") else "agent"


def agent_node(state: State) -> dict:
    """Invoca el LLM con el system prompt (reconstruido en cada turno, ver
    prompts.py) mas el historial de mensajes acumulado en el state.
    """
    system = SystemMessage(content=build_system_prompt())
    response = _llm_con_tools.invoke([system, *state["messages"]])
    return {"messages": [response]}


def build_graph():
    graph = StateGraph(State)
    graph.add_node("guardrail", guardrail_node)
    graph.add_node("agent", agent_node)
    graph.add_node("tools", ToolNode(TOOLS))
    graph.add_edge(START, "guardrail")
    graph.add_conditional_edges("guardrail", _route_after_guardrail)
    graph.add_conditional_edges("agent", tools_condition)
    graph.add_edge("tools", "agent")
    return graph.compile(checkpointer=MemorySaver())


_app = build_graph()


def _build_config(thread_id: str, tags: list[str] | None = None) -> dict:
    """Config comun para invocar el grafo: memoria (thread_id) + traza en
    Langfuse si hay claves configuradas (ver observability.py; si no, el
    agente sigue funcionando igual mas sin trazas).

    NOTA: fijar el trace_id de Langfuse via config["run_id"] no funciona en la
    version instalada del SDK (4.12, arquitectura OTel): el trace_id lo asigna
    el propio exportador y no coincide con el run_id de LangChain (verificado
    contra la API real). Para correlacionar una traza en concreto se usan
    `tags` en su lugar, que si viajan tal cual.
    """
    config: dict = {"configurable": {"thread_id": thread_id}}
    handler = get_langfuse_handler()
    if handler is not None:
        config["callbacks"] = [handler]
        if tags:
            config["metadata"] = {"langfuse_tags": tags}
    return config


def run(pregunta: str, thread_id: str = "cli") -> str:
    """Ejecuta una pregunta contra el agente y devuelve el texto de la respuesta
    final. thread_id separa la memoria conversacional de cada sesion/usuario.
    """
    config = _build_config(thread_id)
    result = _app.invoke({"messages": [("user", pregunta)]}, config=config)
    return result["messages"][-1].content


def _extraer_traza(mensajes_turno: list) -> list[dict]:
    """A partir de los mensajes nuevos de un turno, empareja cada tool_call con
    su ToolMessage de respuesta (por tool_call_id) y produce una traza legible
    para el panel de trazabilidad de la UI. Si el turno se bloqueo en el
    guardrail no habra ningun tool_call y la traza sale vacia.
    """
    resultados_por_id = {
        m.tool_call_id: m.content for m in mensajes_turno if isinstance(m, ToolMessage)
    }
    traza = []
    for m in mensajes_turno:
        for tc in getattr(m, "tool_calls", None) or []:
            traza.append({
                "tool": tc["name"],
                "args": tc["args"],
                "resultado": resultados_por_id.get(tc["id"]),
            })
    return traza


def run_with_trace(
    pregunta: str, thread_id: str = "ui", tags: list[str] | None = None
) -> tuple[str, list[dict]]:
    """Como run(), pero ademas devuelve la traza de tools invocadas en ESTE
    turno (para mostrarla en la UI, o para correlacionar la traza de Langfuse
    con una pregunta del golden dataset via `tags`). No cambia el historial ni
    afecta a run().
    """
    config = _build_config(thread_id, tags=tags)
    mensajes_previos = len(_app.get_state(config).values.get("messages", []))

    result = _app.invoke({"messages": [("user", pregunta)]}, config=config)

    mensajes_turno = result["messages"][mensajes_previos:]
    return result["messages"][-1].content, _extraer_traza(mensajes_turno)
