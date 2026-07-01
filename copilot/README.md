# copilot — agente de analítica conversacional

Proyecto del curso de AI Engineering. Agente que responde preguntas de negocio en
lenguaje natural sobre los datos de Wash & Ready.

## Arquitectura

```
START
  → guardrail   (LLM-as-judge, salida estructurada: permitido / fuera_de_tema /
                 modificacion / inyeccion — corta el turno si no es "permitido")
  → agent       (LLM + system prompt: fecha de hoy, catalogo de centros/servicios,
                 esquema SQL si hay escape configurado)
      ⇄ tools
          · resumen_facturacion, beneficio_neto, facturacion_por_centro,
            buscar_tickets, listar_centros, listar_servicios   (API de Wash & Ready)
          · consultar_sql   (solo si hay WASHREADY_DB_URL: SELECT validada
            contra el usuario de BD de solo lectura copilot_ro)
  → END
```

- El propio nodo `agent` redacta la respuesta final en lenguaje natural tras ver
  el resultado de las tools (no hay un "redactor" separado).
- Memoria conversacional por `thread_id` (checkpointer de LangGraph): cada sesión
  de Streamlit o cada `thread_id` pasado a `run()`/`run_with_trace()` mantiene su
  propio historial.
- **Decisión de diseño (híbrido):** las tools fijas cubren las preguntas
  frecuentes con números garantizados; `consultar_sql` es el escape para la cola
  larga (desgloses por servicio/operario, conteos sobre tablas completas) que
  ninguna tool fija resuelve. El system prompt indica explícitamente al agente
  cuándo usar cada una (`src/prompts.py`).
- **Observabilidad:** cada invocación del grafo pasa por un `CallbackHandler` de
  Langfuse (`src/observability.py`) si hay claves configuradas; si no, el agente
  sigue funcionando igual, solo que sin trazas.

## Seguridad del escape a SQL

Dos capas, pero solo una es la frontera real:

1. **Validador rule-based** (`src/tools/sql_tool.py`, con `sqlparse`): una única
   sentencia, solo `SELECT`/`WITH`, rechaza `INSERT/UPDATE/DELETE/DROP/...` y
   `INTO OUTFILE`/`DUMPFILE`, fuerza un `LIMIT` si no lo trae. Es defensa en
   profundidad, no la barrera principal.
2. **La barrera real:** el usuario MySQL `copilot_ro` solo tiene `SELECT` sobre
   las tablas de negocio, y en `user` únicamente sobre las columnas
   `(id, usuario, role, centro_id, empresa_id)` — nunca `password` ni
   `disabled_from`. Verificado en la práctica: un `UPDATE`/`DELETE`, o un intento
   de leer `user.password` o tablas fuera del whitelist, falla por permisos
   aunque el validador anterior tuviera un fallo.

## Estructura

```
copilot/
├── src/
│   ├── config.py             # Settings desde el .env de la raiz del repo
│   ├── washready_client.py   # Cliente HTTP + login JWT contra la API
│   ├── context.py            # Catalogo (centros/servicios) cacheado para el prompt
│   ├── tools/
│   │   ├── fixed_tools.py    # Las 6 tools sobre la API
│   │   └── sql_tool.py       # validar_sql() + la tool consultar_sql
│   ├── db.py                 # Conexion de solo lectura (copilot_ro)
│   ├── guardrails.py         # Guardrail de entrada (LLM-as-judge)
│   ├── prompts.py            # System prompt (catalogo + esquema SQL condicional)
│   ├── agent.py              # StateGraph: guardrail -> agent <-> tools
│   ├── observability.py      # CallbackHandler de Langfuse cacheado
│   ├── agent_cli.py          # python -m src.agent_cli "pregunta"
│   └── app.py                # UI de chat en Streamlit (streamlit run src/app.py)
└── eval/
    ├── golden.yaml           # 25 preguntas con respuesta esperada real
    ├── judge.py              # LLM-as-judge del harness (compara obtenida vs esperada)
    ├── run_eval.py           # Harness: corre golden.yaml y persiste scores en Langfuse
    └── report.md             # Ultimo resultado (25/25)
```

## Puesta en marcha

```bash
cd copilot
python -m venv .venv
source .venv/Scripts/activate   # Windows: .venv\Scripts\activate

pip install -r requirements.txt

# Configuracion (en la raiz del repo, no dentro de copilot/):
cp ../.env.example ../.env               # y rellena las claves
cp ../.env.langfuse.example ../.env.langfuse   # solo si vas a usar Langfuse
```

Antes de arrancar el agente, necesitas:
- El backend `wash-ready/` corriendo (Java 21 + Maven + MySQL).
- (Opcional) Langfuse local: `docker compose --env-file ../.env.langfuse -f ../docker-compose.langfuse.yml up -d`.
- (Opcional, para el escape SQL) el usuario `copilot_ro` creado en MySQL y su
  password en `WASHREADY_DB_URL` dentro de `.env` — sin él, el agente sigue
  funcionando con solo las tools fijas.

Formas de usar el agente:

```bash
# Linea de comandos, una pregunta suelta
python -m src.agent_cli "¿cuánto se facturó en junio?"

# UI de chat
streamlit run src/app.py

# Evaluacion contra el golden dataset (genera eval/report.md)
python -m eval.run_eval
```