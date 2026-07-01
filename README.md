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
preguntas de negocio ("¿cuánto facturé este mes en el centro Sur?", "¿qué servicio
se vende menos?", "¿qué operario hizo más pagos en bono?") y hoy tiene que navegar
dashboards o pedir consultas SQL a alguien. La información existe, pero no es
accesible en lenguaje natural.

## 2. ¿Cómo lo resolvemos?

Un **agente conversacional** (LangGraph) con herramientas que consultan Wash & Ready:

- **Tools fijas** sobre la API/informes existentes → respuestas con números
  garantizados para las preguntas frecuentes.
- **Escape a text-to-SQL** (usuario de BD de solo lectura + guardrails) para la
  cola larga de preguntas no previstas.
- **Guardrails** de entrada/salida y **observabilidad** con Langfuse.
- UI de chat en Streamlit.

Arquitectura y decisiones en [`copilot/README.md`](copilot/README.md).

## 3. ¿Conseguimos resolverlo?

Evaluación sobre un **golden dataset** de ~25 preguntas con respuesta esperada:
*accuracy* numérica, % de consultas válidas y comparativa tools-fijas vs text-to-SQL.
Resultados en [`copilot/eval/`](copilot/eval/).

## 4. ¿Cuáles son los siguientes pasos?

Memoria conversacional, más herramientas (export a PDF/Excel reutilizando el
`PdfService` del backend), alertas proactivas de anomalías (caídas de facturación,
picos de bonos) y despliegue multi-tenant por empresa.

---

## Estructura del repositorio

```
washready-copilot/
├── wash-ready/     # TFG previo: backend Spring Boot (base de datos + API). Base, no evaluable.
└── copilot/        # Proyecto del curso: agente Python.
```
