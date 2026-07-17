# 🐳 Onboarding — DEV (Docker Compose, the full stack)

_Run the whole system end to end in one Docker Compose command — real Postgres, Kafka, Redis,
and observability; the closest thing to production on your machine._

**Contents:** [Prerequisites](#prerequisites) · [Bring the stack up](#1-bring-the-stack-up) ·
[Everything you can open](#2-everything-you-can-open) ·
[Prove it end to end](#3-prove-it-end-to-end) · [Day-2 commands](#4-day-2-commands)

## Prerequisites

- Docker Desktop (compose v2.24+)
- Optional: `ANTHROPIC_API_KEY` / `VOYAGE_API_KEY` exported for chat + RAG
  ([How To — LLM, RAG & MCP](how-to/llm-rag-mcp.md)); without them everything else works

## 1. Bring the stack up

```bash
cd Backend
docker compose up --build          # first build takes a few minutes
```

**Skip the build** with the CI-built images CD publishes on every merge (public, no login):

```bash
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d --no-build
# pin a build: AI_SANDBOX_TAG=sha-abc1234 docker compose ... up -d --no-build
```

## 2. Everything you can open

| What | URL | Credentials |
|---|---|---|
| **App (SPA)** | http://localhost:4200 | demo login `demo` / `demo` |
| Grafana (system view) | http://localhost:4200/grafana | anonymous read-only; `admin`/`admin` to edit |
| Auth Swagger | http://localhost:8085/swagger-ui.html | Bearer token from `POST /auth/login` |
| Audit Swagger | http://localhost:8083/swagger-ui.html | same token |
| Kafka UI | http://localhost:8080 | auto-connected |
| Adminer (Postgres GUI) | http://localhost:8082 | server `postgres`, `audit`/`audit`, db `auditdb` |
| Redis Insight | http://localhost:5540 | add DB: host `redis`, port `6379`, no auth |
| Prometheus | http://localhost:9090 | — |
| MCP endpoint | `POST http://localhost:8083/mcp` | none (public corpus) |

Backing stores from the host: Postgres `localhost:5432` (`audit`/`audit`), Redis
`localhost:6379`, Kafka `localhost:19092`.

## 3. Prove it end to end

1. Sign in (`demo`/`demo`) → **Observability**: your own LOGIN event appears as a row within a
   few seconds — that's Auth → Kafka → Audit consumer → Postgres, live.
2. **Chat** (with keys): ask "How does the RAG pipeline behind this chat work?" — grounded answer
   with the retrieval pipeline described.
3. Grafana → *ask-app Overview*: request rates and live logs; Explore → Tempo: find the login as
   **one trace** across the Kafka hop.

Or let Playwright do it: `cd ../e2e && npm ci && npx playwright install chromium && npx playwright test`
— 4 flows against this running stack, nothing mocked.

## 4. Day-2 commands

```bash
docker compose ps                          # what's running
docker compose logs -f audit               # follow one service
docker compose up -d --build audit         # rebuild one service after a change
docker compose --profile local-tools down  # stop everything (incl. the GUI tools)
```

Scaling proof (2 Auth replicas behind nginx, Redis-backed refresh tokens):
`docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --build`.

**Deeper dives:** [How To — Grafana](how-to/grafana.md) ·
[Kafka](how-to/kafka.md) · [Postgres](how-to/postgres.md) · [Redis](how-to/redis.md) ·
[Prometheus/Loki/Tempo](how-to/prometheus-loki-tempo.md)

**Next level up:** [Onboarding — PROD (GCE deployment)](onboarding-prod.md).
