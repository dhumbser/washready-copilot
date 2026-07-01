"""Grafo del agente: LLM + tools fijas, con memoria conversacional por thread_id.

    START -> agent -> (tools_condition) -> tools -> agent -> ... -> END

El nodo `agent` queda aislado en su propia funcion para poder anteponerle un
nodo guardrail_in en el Dia 3 sin tener que tocar esta logica.
"""

from __future__ import annotations

from typing import Annotated, TypedDict

from langchain_core.messages import SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

from src.config import settings
from src.prompts import build_system_prompt
from src.tools import TOOLS


class State(TypedDict):
    messages: Annotated[list, add_messages]


_llm = ChatOpenAI(model=settings.copilot_model, api_key=settings.openai_api_key, temperature=0)
_llm_con_tools = _llm.bind_tools(TOOLS)


def agent_node(state: State) -> dict:
    """Invoca el LLM con el system prompt (reconstruido en cada turno, ver
    prompts.py) mas el historial de mensajes acumulado en el state.
    """
    system = SystemMessage(content=build_system_prompt())
    response = _llm_con_tools.invoke([system, *state["messages"]])
    return {"messages": [response]}


def build_graph():
    graph = StateGraph(State)
    graph.add_node("agent", agent_node)
    graph.add_node("tools", ToolNode(TOOLS))
    graph.add_edge(START, "agent")
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
