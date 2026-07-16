# 🧩 Component map

_Part of the [ask-app](../README.md) documentation._

| Area | Responsibility | Main location |
|---|---|---|
| UI | Angular routes, auth state, dashboard, chat, GA4 and Sentry | `UI/src/app/` |
| Edge | TLS, SPA hosting, same-origin proxying, production Grafana route | `Backend/deploy/Caddyfile`, `UI/nginx.conf` |
| Auth | OAuth/demo login, JWT/JWKS, refresh token lifecycle, event producer | `Backend/Auth/` |
| Audit | Audit APIs, Kafka consumer, RAG, chat, MCP | `Backend/Audit/` |
| Shared library | `AuditEvent` contract and per-instance request limiter | `Backend/common/` |
| Data | Audit records, RAG vectors, refresh tokens, event log | PostgreSQL/pgvector, Redis, Kafka |
| Observability | Metrics, logs, traces, dashboards | `Backend/monitoring/` |
