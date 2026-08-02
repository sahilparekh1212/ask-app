# ask-app — production runtime configuration (authoritative)

This document states the configuration that is **actually in effect in the production deployment**
(`https://ask-app.sahilparekh1212.com` — a single GCP Compute Engine VM with TLS terminated by
Caddy). It is the authoritative source for "what is enabled or configured in production".

**Source config vs. production — how to read it:** the repository's config files
(`application.properties`, `docker-compose.yml`, etc.) show **defaults for LOCAL and lower
environments**, where env-gated features default to *disabled* or *empty* — for example
`sentry.dsn=${SENTRY_DSN:}` is empty, `rag.vector-store` defaults to `memory`, and
`assistant.api-key=${ANTHROPIC_API_KEY:}` is empty. In the production deployment those environment
variables **are set** (shipped by the GitHub Actions deploy as the VM's `.env`). So a feature being
default-off in the source does **not** mean it is off in production.

## Enabled in production

- **LLM chat assistant** — enabled. `ANTHROPIC_API_KEY` is set; model `claude-opus-4-8`. Serves
  `POST /api/v1/assistant/chat` through the server-side proxy.
- **RAG grounding** — enabled with **pgvector** (`RAG_VECTOR_STORE=pgvector`, `RAG_ENABLED=true`,
  `VOYAGE_API_KEY` set) — not the in-memory store, which is only the LOCAL/test default. Two-stage
  retrieval with the Voyage cross-encoder reranker (`RAG_RERANK_ENABLED=true`, `rerank-2.5-lite`).
- **MCP server** (`POST /mcp`) — enabled; public and unauthenticated by design.
- **Voice chat** — enabled: dictation and read-aloud, with the natural **Google Cloud
  Text-to-Speech** neural voice active because `GOOGLE_TTS_API_KEY` is set (voice
  `en-US-Chirp3-HD-Kore`), plus the hands-free voice-only mode.
- **Sentry error monitoring** — enabled. Per-service DSNs (`SENTRY_DSN_AUTH`, `SENTRY_DSN_AUDIT`)
  are set, so both backend services and the Angular SPA report errors; `sentry.send-default-pii=false`.
  Sentry issues are also reachable from Grafana via the installed Sentry datasource.
- **AI answer-quality tracing** — enabled (`AI_TRACE_ENABLED=true`): per-interaction retrieval
  rankings, latencies, and token usage are recorded (no user identity is stored).
- **Authentication** — Google OAuth2 (`GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` set) and the demo
  login (`demo` / `demo`); RSA-signed JWTs verified via JWKS.
- **Observability** — Prometheus metrics, Loki logs, Tempo traces, and Grafana (Grafana published
  read-only in production).
- **Rate limiting**, **RBAC**, and the Kafka-backed **audit trail** — all active.
- **UI feature flags** — enabled and all ON. The SPA reads `GET /api/v1/meta/flags` (from the
  `feature_flags` table, seeded ON) to show/hide chat, voice, hints, and observability. They are
  read-only from the app; an operator flips a feature by updating the row in the production database
  (no redeploy). See ADR-0015.
- **CORS** — restricted to the production domain via `CORS_ALLOWED_ORIGINS`.

## Not enabled in production

- **Demo audit-log seeding** and `POST /api/v1/audit-logs/demo` — LOCAL/DEV only; they do not run
  in production and the endpoint returns 404 there.
- **H2 console** — disabled; production uses PostgreSQL.
- **Swagger UI / OpenAPI** — served by the app, but the service ports (8083/8085) are not published
  in production (only Caddy's 80/443), so Swagger is a local/dev tool there rather than a public URL.
