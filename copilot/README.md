# copilot — agente de analítica conversacional

Proyecto del curso de AI Engineering. Agente que responde preguntas de negocio
sobre los datos de Wash & Ready.

## Arquitectura

```
Usuario (Streamlit)
   -> Guardrail de entrada (on-topic, no DML)
   -> Agente LangGraph  [system prompt + esquema + few-shot]
        -> Tools fijas: resumen_informe(), buscar_tickets(), ...   (sobre la API de W&R)
        -> Escape: consultar_sql(sql)  [BD read-only + validacion solo-SELECT]
   -> Redactor LLM -> respuesta NL + tabla/grafico
   (Langfuse traza cada paso: tokens, latencia, SQL)
```

Decisión de diseño: empezamos por **tools fijas** (seguras, números garantizados)
y añadimos **text-to-SQL** como modo avanzado para preguntas no previstas. La
evaluación compara ambas ramas.

## Estructura

```
copilot/
├── src/        # código del agente (tools, grafo, guardrails, UI)
├── eval/       # golden dataset + harness de evaluación
└── requirements.txt
```

## Puesta en marcha

```bash
cd copilot
python -m venv .venv && source .venv/Scripts/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp ../.env.example ../.env   # y rellena las claves
streamlit run src/app.py
```
