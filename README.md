# washready-copilot

Agente conversacional que responde preguntas de negocio en lenguaje natural sobre
los datos de **Wash & Ready** (facturación, tickets, servicios, centros).
Proyecto del curso de **AI Engineering**.

> **Qué es de quién:** `wash-ready/` es mi TFG previo (aplicación Spring Boot que
> aporta la base de datos y la API); **`copilot/` es el proyecto de este curso**:
> el agente construido sobre esos datos reales.

---

## 1. ¿Cuál es el problema?

El dueño/encargado de Wash & Ready —perfil **no técnico**— necesita responder
preguntas de negocio ("¿cuánto facturé este mes?", "¿qué servicio se vende menos?",
"¿qué operario ha facturado más?") y hoy tiene que navegar dashboards o pedir
consultas SQL a alguien. La información existe en la base de datos, pero no es
accesible en lenguaje natural.

## 2. ¿Cómo lo resolvemos?

Un **agente conversacional** (LangGraph) sobre los datos reales de Wash & Ready:

```
START → guardrail (LLM-as-judge: rechaza off-topic/modificación/inyección)
       → agent (LLM + system prompt con catálogo y esquema)
           ⇄ tools:
               · 6 tools fijas sobre la API REST (informes, dashboard, tickets, catálogo)
               · consultar_sql: escape a text-to-SQL de solo lectura para la cola larga
                 (desgloses por servicio/operario, conteos sobre tablas completas)
       → END
```

- **Tools fijas** (`resumen_facturacion`, `beneficio_neto`, `facturacion_por_centro`,
  `buscar_tickets`, `listar_centros`, `listar_servicios`) dan números garantizados
  para las preguntas frecuentes, envolviendo la API existente de Wash & Ready.
- **Escape a text-to-SQL** contra un usuario MySQL de solo lectura (`copilot_ro`,
  con `SELECT` restringido a las tablas de negocio y solo a columnas concretas de
  `user`, sin `password` ni `disabled_from`) para lo que ninguna tool fija cubre.
  Doble capa de defensa: un validador rule-based (una sola sentencia, solo
  `SELECT`/`WITH`, sin DML/DDL) *y*, de fondo, los privilegios reales de la
  cuenta de BD — verificado que un `UPDATE`/`DELETE` falla por permisos aunque el
  validador fallara.
- **Guardrail de entrada** (LLM-as-judge con salida estructurada) bloquea preguntas
  fuera de tema, intentos de modificar datos e inyección de prompt antes de que
  lleguen al agente.
- **Observabilidad con Langfuse** (self-hosted vía Docker): cada turno queda
  trazado con tokens, coste, latencia y las tools invocadas.
- **UI de chat en Streamlit** con panel de trazabilidad (qué tools se usaron y con
  qué argumentos en cada respuesta).

Arquitectura y decisiones detalladas en [`copilot/README.md`](copilot/README.md).

## 3. ¿Conseguimos resolverlo?

Evaluación automática sobre un **golden dataset de 25 preguntas** con respuesta real
calculada sobre los datos (15 de rama fija + 10 que requerían el escape SQL),
comparadas con un LLM-as-judge y con cada resultado persistido como *score* en
Langfuse:

| Momento | Global | Rama fija | Rama SQL |
|---|---|---|---|
| Antes del escape SQL (solo tools fijas) | 21/25 (84%) | 15/15 (100%) | 6/10 — el agente declinaba correctamente por no tener la herramienta |
| **Con el escape a text-to-SQL** | **25/25 (100%)** | **15/15 (100%)** | **10/10 (100%) — acertando el dato real** |

La mejora es medible y no es casualidad: antes del escape SQL, preguntas como
*"¿cuántos clientes distintos tenemos?"* hacían que el agente **extrapolara desde
una muestra parcial** de `buscar_tickets` (paginada a 8 filas) en vez de reconocer
su límite — por ejemplo, respondía 3 clientes cuando la cifra real era 14. Con
`consultar_sql` disponible, el agente agrega sobre *todos* los datos y acierta.

El proceso también encontró y corrigió un **error propio en el golden dataset**
(un cálculo de referencia inconsistente con la convención de negocio del resto del
dataset, verificado con SQL directo antes de corregirlo) — la evaluación no solo
mide al agente, también obliga a auditar el oráculo contra el que se mide.

Detalle completo: [`copilot/eval/report.md`](copilot/eval/report.md) y las trazas
en Langfuse (`localhost:3000`).

## 4. ¿Cuáles son los siguientes pasos?

- **Sugerencia proactiva de métricas relacionadas**: al preguntar por facturación
  bruta, ofrecer también el beneficio neto (descontando adelantos) sin que el
  usuario tenga que saber que existe esa distinción.
- **Multi-tenant real**: el agente corre hoy con permisos de administrador (todos
  los centros); el siguiente paso natural es acotarlo por rol/centro como ya hace
  el backend con los operarios.
- **Más tools de exportación**: PDF/Excel reutilizando el `PdfService` ya existente
  del backend.
- **Alertas proactivas** de anomalías (caídas de facturación, picos de bonos) en
  vez de depender de que el usuario pregunte.
- **Validador SQL más robusto**: el actual es rule-based sobre el texto (con un
  falso positivo conocido y aceptado: rechaza consultas inocuas si contienen una
  palabra prohibida dentro de un literal de texto) — un parser consciente de
  tokens eliminaría ese caso, aunque la frontera de seguridad real ya la da el
  usuario de BD de solo lectura.

---

## Estructura del repositorio

```
washready-copilot/
├── wash-ready/                   # TFG previo: backend Spring Boot (base de datos + API). Base, no evaluable.
├── copilot/                      # Proyecto del curso: agente Python.
│   └── src/
│       ├── config.py             # Settings desde .env
│       ├── washready_client.py   # Cliente HTTP + login JWT contra la API de Wash & Ready
│       ├── context.py            # Catálogo (centros/servicios) cacheado para el prompt
│       ├── tools/                # 6 tools fijas + consultar_sql (escape SQL, condicional)
│       ├── db.py                 # Conexión de solo lectura (copilot_ro) para el escape SQL
│       ├── guardrails.py         # Guardrail de entrada (LLM-as-judge)
│       ├── prompts.py            # System prompt (catálogo + esquema SQL condicional)
│       ├── agent.py              # Grafo LangGraph: guardrail → agent ⇄ tools
│       ├── observability.py      # Instrumentación con Langfuse
│       ├── agent_cli.py          # Runner de línea de comandos
│       └── app.py                # UI de chat en Streamlit
│   └── eval/
│       ├── golden.yaml           # 25 preguntas con respuesta esperada real
│       ├── judge.py              # LLM-as-judge del harness
│       ├── run_eval.py           # Harness: corre el golden set y persiste scores en Langfuse
│       └── report.md             # Último resultado (25/25)
├── docker-compose.langfuse.yml   # Stack de Langfuse self-hosted (oficial, sin modificar)
└── .env.example / .env.langfuse.example   # Plantillas de configuración (sin secretos)
```

## Puesta en marcha

1. `cp .env.example .env` y `cp .env.langfuse.example .env.langfuse`; rellena las
   claves (ver comentarios de cada archivo).
2. Backend: arranca `wash-ready/` (requiere Java 21, Maven y MySQL).
3. Langfuse: `docker compose --env-file .env.langfuse -f docker-compose.langfuse.yml up -d`.
4. Agente: instrucciones en [`copilot/README.md`](copilot/README.md).

> ⚠️ Nunca subas los archivos `.env`/`.env.langfuse` ni credenciales reales. La
> configuración sensible se externaliza mediante variables de entorno.