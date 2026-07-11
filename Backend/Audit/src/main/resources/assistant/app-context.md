# AI-Sandbox — application overview (assistant grounding document)

AI-Sandbox is a production-shaped backend engineering portfolio project: two Spring Boot
microservices plus an Angular 19 SPA, connected by Kafka, backed by Postgres, and observed by
Prometheus/Grafana/Loki/Tempo. Everything runs locally with one `docker compose up --build`
from `Backend/`.

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
  server-side proxy in the Audit service.
- Flashcards page: generates a Q&A study deck about the app via the same proxy.
- An HTTP interceptor attaches `Authorization: Bearer <token>` to our APIs only and silently
  refreshes once on 401.

## Cross-cutting
- **Rate limiting**: newest-wins per user+endpoint — a newer request supersedes the active
  one, which rolls back and returns 429 with Retry-After.
- **RBAC**: JWTs carry a `roles` claim; admin-gated endpoints use `@PreAuthorize`.
- **Observability**: Prometheus metrics (with p95/p99 latency histograms), Loki logs, Tempo
  distributed traces that follow a request across the Kafka hop, Grafana dashboards.
- **CI/CD**: GitHub Actions — build + tests with a 90% line-coverage gate, k6 load tests,
  gitleaks, CodeQL SAST, Trivy CVE scans of jars and Docker images, Dependabot.
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
