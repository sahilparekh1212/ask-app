# ask-app — application overview (assistant grounding document)

ask-app is a production-shaped backend engineering portfolio project: two Spring Boot
microservices plus an Angular 21 SPA, connected by Kafka, backed by Postgres, observed by
Prometheus/Grafana/Loki/Tempo, and error-monitored by Sentry. Everything runs locally with one
`docker compose up --build` from `Backend/`.

## Services

### Auth service (port 8085)
- Sign-in options: **Google OAuth2** (`/oauth2/authorization/google`) or a **demo login**
  (`POST /auth/login` with `{"username","password"}`, optional `"role":"ROLE_ADMIN"`) that
  requires no identity-provider setup.
- Issues RSA-signed JWTs (access + refresh). Public keys are served at
  `/.well-known/jwks.json`; other services verify tokens against that JWKS endpoint.
- Tokens carry `sub`, `email`, `name`, and a `roles` claim (`ROLE_USER` or `ROLE_ADMIN`).
- `POST /auth/refresh` rotates the single-use refresh token; `POST /auth/logout` revokes it;
  `GET /auth/me` returns the current user's profile.
- Refresh tokens live in memory by default, or in Redis (`auth.refresh-token.store=redis`)
  so multiple replicas can share them.
- Publishes LOGIN / TOKEN_REFRESH / LOGOUT audit events to the Kafka topic `audit.events`
  (fire-and-forget, so a broker outage never blocks sign-in).

### Audit service (port 8083)
- Consumes `audit.events` from Kafka and persists audit rows idempotently (deduplicated by
  `eventId`); failed messages retry, then dead-letter to `audit.events.DLT`.
- REST API under `/api/v1/audit-logs`:
  - `GET /search` — paginated, filtered (entityType, action, details substring, date range),
    sort-whitelisted search.
  - `GET /stats` — database-side aggregation: total count plus per-action and per-entityType
    breakdowns, honouring the same filters as `/search`.
  - `POST /demo` — bulk-insert 1..500 realistic demo rows (LOCAL/DEV profiles only).
  - `DELETE /{id}` — soft delete; **admin role required**.
- Audit rows are immutable after creation (no update path; soft delete is the only mutation).
- Schema is owned by Liquibase; H2 locally, Postgres in the compose stack.

## Frontend (Angular SPA, port 4200)
- Login page (demo + Google), OAuth callback, profile page.
- Audit dashboard: server-side paginated/sortable table, filter dropdowns, details
  contains-search, CSS bar-chart stats, demo-log generator button.
- Assistant chat page: asks questions about the app, answered by a Claude model through a
  server-side proxy in the Audit service. It grounds answers on the repo's docs/source (RAG)
  and, only for questions about the app's live activity, on the audit stats/rows.
- Voice chat: mic dictation and read-aloud of replies via the browser's Web Speech API, upgraded
  to a natural Google Cloud Text-to-Speech neural voice through a server-side `/speak` proxy (it
  falls back to the browser voice when no TTS key is set). A hands-free "voice only" mode listens,
  answers aloud in a short spoken reply, then listens again.
- An HTTP interceptor attaches `Authorization: Bearer <token>` to our APIs only and silently
  refreshes once on 401.

## Cross-cutting
- **Rate limiting**: newest-wins per user+endpoint — a newer request supersedes the active
  one, which rolls back and returns 429 with Retry-After.
- **RBAC**: JWTs carry a `roles` claim; admin-gated endpoints use `@PreAuthorize`.
- **Observability**: Prometheus metrics (with p95/p99 latency histograms), Loki logs, Tempo
  distributed traces that follow a request across the Kafka hop, and Grafana dashboards.
- **Error monitoring**: **Sentry** captures unhandled exceptions in **both** backend services
  (a separate Sentry project per service, `sentry.send-default-pii=false`) and uncaught errors in
  the Angular SPA (`@sentry/angular`, initialised before bootstrap). It is enabled whenever a Sentry
  DSN is configured — which it **is in the production deployment** — and cleanly disabled when the
  DSN is empty (LOCAL/dev). Sentry issues are also reachable from Grafana via the installed Sentry
  datasource, so errors sit alongside the metrics/logs/traces.
- **AI answer-quality tracing**: every chat and MCP-search interaction is recorded (query,
  pre/post-rerank retrieval rankings with scores, model, reply, latencies, tokens) to measure and
  improve answer accuracy; Micrometer meters feed Grafana. It stores no user identity.
- **CI/CD**: GitHub Actions — build + tests with a 90% line-coverage gate, k6 load tests,
  gitleaks, CodeQL SAST, Trivy CVE scans of jars and Docker images, Dependabot, PIT mutation
  testing, and Playwright E2E; images are cosign-signed with SBOM attestations and deployed keylessly
  via Workload Identity Federation.
- Architecture decisions are recorded as ADRs in `Backend/docs/adr/`.

## API documentation (Swagger / OpenAPI)

Each backend service serves interactive **Swagger UI** and a raw **OpenAPI spec** via springdoc.
Both are public — no authentication is needed to view them (see each service's `SecurityConfig`,
which permits `/swagger-ui/**`, `/swagger-ui.html`, and `/v3/api-docs/**`):

- **Audit service** (port 8083):
  - Swagger UI: `http://localhost:8083/swagger-ui.html`
  - OpenAPI JSON: `http://localhost:8083/v3/api-docs`
- **Auth service** (port 8085):
  - Swagger UI: `http://localhost:8085/swagger-ui.html`
  - OpenAPI JSON: `http://localhost:8085/v3/api-docs`

The paths are set in each service's `application.properties`
(`springdoc.swagger-ui.path=/swagger-ui.html`, `springdoc.api-docs.path=/v3/api-docs`). These URLs
work whenever the service is reachable on its port — running it directly, or via
`docker compose up` (which publishes 8083 and 8085). In the production deployment only Caddy's
80/443 are published and the service ports are withdrawn, so Swagger UI is a local/development tool
there rather than a public URL. In CI, the `api-contract` workflow generates these OpenAPI specs
from the running services and fails a PR on breaking API changes.

## What's enabled in the production deployment

The live site (`ask-app.sahilparekh1212.com`) runs on a single GCP Compute Engine VM with TLS
terminated by Caddy. Every feature below is **active in that production deployment** (each is gated
by a key or flag that is set in prod):

- **LLM chat assistant** — Claude via the server-side proxy, grounded by **RAG**: pgvector on
  Postgres with Voyage embeddings, content-hash incremental indexing, and **two-stage retrieval**
  (cosine recall then a Voyage cross-encoder reranker). A 100-question retrieval-quality gate
  (recall@k / MRR / nDCG) guards it in CI.
- **MCP server** (`POST /mcp`) — semantic knowledge search over this corpus for any MCP client.
- **Voice chat** — Web Speech dictation, Google Cloud Text-to-Speech read-aloud, and a hands-free
  voice-only conversation mode.
- **AI answer-quality tracing** — per-interaction retrieval rankings, latencies, and token usage.
- **Sentry error monitoring** — both backend services and the SPA (details under Cross-cutting).
- **Observability** — Prometheus, Loki, Tempo, and Grafana (published read-only in prod).
- **Rate limiting**, **RBAC**, a Kafka-backed **audit trail**, and **Google OAuth + demo login**.
- Shipped by a keyless GitHub Actions pipeline (Workload Identity Federation) with cosign image
  signing and SBOM attestations.

If asked what is running, enabled, or active in production, treat this list — including Sentry — as
the current answer.
