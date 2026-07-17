# 🧑‍💻 Onboarding — LOCAL (bare metal, no Docker)

_Run the whole app end to end on your machine with just a JDK and Node — in-memory H2, no
Docker, no external services._

**Contents:** [Prerequisites](#prerequisites) · [Start the backend](#1-start-the-backend) ·
[Start the UI](#2-start-the-ui) · [Sign in and click through](#3-sign-in-and-click-through) ·
[Run the tests](#4-run-the-tests) · [Optional extras](#optional-extras-at-this-level)

## Prerequisites

- JDK 17 (`java -version` → 17.x) — Gradle comes via the wrapper
- Node 22 + npm (for the UI)
- Nothing else: no Docker, no database, no broker

## 1. Start the backend

```bash
cd Backend
./gradlew :Auth:bootRun     # first — Audit validates JWTs against Auth's JWKS
# in a second terminal:
./gradlew :Audit:bootRun
```

Defaults under LOCAL: H2 in-memory (console at `http://localhost:8083/h2-console`, JDBC
`jdbc:h2:mem:auditdb`, user `sa`, blank password), ~15 demo audit rows seeded, telemetry
console-only. **Kafka is optional here** — publishing is fire-and-forget, so with no broker the
app still works and audit events from Auth are simply dropped ([ADR-0006](../Backend/docs/adr/0006-fire-and-forget-audit-events.md)).
To run the real pipeline, start the broker first: see [How To — Kafka](how-to/kafka.md).

## 2. Start the UI

```bash
cd UI
npm ci
npm start                   # ng serve → http://localhost:4200
```

The dev server proxies `/auth-api` → `:8085` and `/audit-api` → `:8083`
([`proxy.config.js`](../UI/proxy.config.js)), so the browser only talks to `:4200` — same
single-origin model as production, no CORS.

## 3. Sign in and click through

- Open `http://localhost:4200` → demo login `demo` / `demo` (add role `ROLE_ADMIN` on the form
  to try admin endpoints).
- **Observability page**: the seeded audit rows, KPI cards, charts.
- **Chat**: needs `ANTHROPIC_API_KEY` exported before starting Audit; RAG grounding additionally
  needs `VOYAGE_API_KEY`. Without keys the chat answers 503 and everything else works — see
  [How To — LLM, RAG & MCP](how-to/llm-rag-mcp.md).
- Swagger: `http://localhost:8085/swagger-ui.html` (Auth), `http://localhost:8083/swagger-ui.html`
  (Audit) — use **Authorize** with a token from `POST /auth/login`.

## 4. Run the tests

```bash
cd Backend && ./gradlew build     # all modules: tests + 90% coverage gate
cd UI && npm run test:ci          # headless unit tests
```

## Optional extras at this level

| Want | Do |
|---|---|
| Real Kafka pipeline | `docker compose -f Backend/kafka/docker-compose.yml up -d`, then bootRun with `KAFKA_BOOTSTRAP_SERVERS=localhost:19092` — [How To — Kafka](how-to/kafka.md) |
| Metrics/logs/traces in Grafana | Telemetry is off under LOCAL; run the monitoring stack and switch the services to `DEV` — [How To — Grafana](how-to/grafana.md) |
| Google sign-in | Create OAuth credentials (redirect URI `http://localhost:8085/login/oauth2/code/google`), export `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` |

**Next level up:** [Onboarding — DEV (Docker Compose)](onboarding-dev.md) — the full 12-container
stack with Postgres, Kafka, and observability, one command.
