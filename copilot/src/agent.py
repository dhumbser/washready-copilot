"""Grafo del agente: guardrail de entrada + LLM/tools fijas, con memoria
conversacional por thread_id.

    START -> guardrail -> (bloqueado?) -> END
                        -> agent -> (tools_condition) -> tools -> agent -> ... -> END

El nodo `agent` estaba aislado desde el Dia 2 precisamente para poder anteponer
aqui el guardrail sin tocar su logica interna.
"""

from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

from src.config import settings
from src.guardrails import evaluar_entrada, mensaje_rechazo
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


def run(pregunta: str, thread_id: str = "cli") -> str:
    """Ejecuta una pregunta contra el agente y devuelve el texto de la respuesta
    final. thread_id separa la memoria conversacional de cada sesion/usuario.
    """
    config = {"configurable": {"thread_id": thread_id}}
    result = _app.invoke({"messages": [("user", pregunta)]}, config=config)
    return result["messages"][-1].content
