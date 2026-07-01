"""UI de chat en Streamlit sobre el agente washready-copilot.

Uso (desde copilot/, con el backend wash-ready arrancado y OPENAI_API_KEY
en el .env de la raiz del repo):
    streamlit run src/app.py
"""

from __future__ import annotations

import uuid

import streamlit as st

from src.agent import run_with_trace

st.set_page_config(page_title="washready-copilot", page_icon="🚗")

st.title("🚗 washready-copilot")
st.caption("Pregúntale a los datos de Wash & Ready en lenguaje natural.")

if "thread_id" not in st.session_state:
    st.session_state.thread_id = str(uuid.uuid4())
if "historial" not in st.session_state:
    st.session_state.historial = []  # [{"pregunta": str, "respuesta": str, "traza": list[dict]}]

EJEMPLOS = [
    "¿Cuánto se facturó en junio de 2026?",
    "¿Cuánto facturó el centro Aeropuerto en junio?",
    "¿Cuántos tickets están pendientes de pago este mes?",
    "¿Cuántos centros de trabajo tenemos?",
]

with st.sidebar:
    st.subheader("Ejemplos")
    ejemplo_click = None
    for ejemplo in EJEMPLOS:
        if st.button(ejemplo, use_container_width=True):
            ejemplo_click = ejemplo


def _pintar_traza(traza: list[dict]) -> None:
    if not traza:
        return
    with st.expander("🔧 Herramientas usadas"):
        for paso in traza:
            st.markdown(f"**`{paso['tool']}`**  \nArgs: `{paso['args']}`")
            if paso["resultado"] is not None:
                st.code(paso["resultado"], language="json")


# Repintar el historial de turnos anteriores de esta sesion
for turno in st.session_state.historial:
    with st.chat_message("user"):
        st.write(turno["pregunta"])
    with st.chat_message("assistant"):
        st.write(turno["respuesta"])
        _pintar_traza(turno["traza"])

pregunta = st.chat_input("Escribe tu pregunta...") or ejemplo_click

if pregunta:
    with st.chat_message("user"):
        st.write(pregunta)

    with st.chat_message("assistant"):
        with st.spinner("Pensando..."):
            respuesta, traza = run_with_trace(pregunta, thread_id=st.session_state.thread_id)
        st.write(respuesta)
        _pintar_traza(traza)

    st.session_state.historial.append(
        {"pregunta": pregunta, "respuesta": respuesta, "traza": traza}
    )
