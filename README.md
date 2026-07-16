# ask-app

👋 **Welcome!** Thanks for stopping by. This is a production-shaped full-stack
portfolio project — a live, deployed system rather than a demo — and this README
walks you through what it is, how the pieces connect, and the CI/CD that ships
it. Feel free to poke around the <a href="https://ask-app.sahilparekh1212.com" target="_blank" rel="noopener noreferrer">live app</a> (demo login `demo` / `demo`)
while you read.

This README combines the project overview with the code-verified runtime
architecture. It is intentionally focused on how the deployed system is
connected and why its boundaries exist.

Source: <a href="https://github.com/sahilparekh1212/ask-app" target="_blank" rel="noopener noreferrer">github.com/sahilparekh1212/ask-app</a>

## 📦 What this project is

ask-app is a production-shaped full-stack portfolio application:

- Angular 21 single-page application served by nginx;
- Spring Boot Auth and Audit services;
- Google OAuth2, RSA-signed JWTs, JWKS, and role-based access control;
- asynchronous audit events through Kafka/Redpanda;
- PostgreSQL and pgvector for audit data and repository RAG;
- a server-side Claude assistant and public MCP knowledge-search endpoint;
- Prometheus, Loki, Tempo, Grafana, Google Analytics 4, and Sentry; and
- Docker/OpenShift deployment definitions plus CI/CD and supply-chain checks.

The live app is available at
<a href="https://ask-app.sahilparekh1212.com" target="_blank" rel="noopener noreferrer">ask-app.sahilparekh1212.com</a>. The demo
account is `demo` / `demo`.

## 🗺️ System diagram

```mermaid
flowchart LR
    Browser[Browser user] --> Caddy[Caddy: TLS edge]
    MCP[MCP client] -->|POST /audit-api/mcp| Caddy
    Caddy --> UI[nginx: Angular SPA]
    Caddy -->|/grafana in production| Grafana[Grafana]

    UI -->|/auth-api| Auth[Auth service]
    UI -->|/audit-api| Audit[Audit service]

    Auth -->|OAuth authorization / token exchange| Google[Google OAuth2]
    Google -->|OAuth callback| Auth
    Audit -->|fetch public signing keys| Auth
    Auth <--> Redis[(Redis\nrefresh-token store)]
    Auth -->|async audit.events| Kafka[[Kafka / Redpanda]]
    Audit -->|async AI-feature events| Kafka
    Kafka -->|idempotent consume| Audit

    Audit <--> Postgres[(PostgreSQL\naudit_logs + pgvector)]
    Corpus[Bundled source + docs] -->|startup indexing| Audit
    Audit -->|embed/search| Voyage[Voyage AI]
    Audit -->|grounded chat| Claude[Anthropic Claude]

    UI -. page views when configured .-> GA4[Google Analytics 4]
    UI -. frontend errors .-> Sentry[Sentry]
    Auth -. backend errors .-> Sentry
    Audit -. backend errors .-> Sentry

    Prometheus[Prometheus] -->|scrapes metrics| Auth
    Prometheus -->|scrapes metrics| Audit
    Auth -. logs .-> Loki[Loki]
    Audit -. logs .-> Loki
    Auth -. traces .-> Tempo[Tempo]
    Audit -. traces .-> Tempo
    Prometheus --> Grafana
    Loki --> Grafana
    Tempo --> Grafana
```

## 🎨 Frontend (UI)

An Angular 21 single-page app, hand-rolled with **no UI framework** — one CSS
design-token layer (a GitHub-dark palette) drives the theme, and state is
**signals-first** (`computed`, no global store; RxJS only at the HTTP edge).

- **Stateless client auth** — a functional HTTP interceptor attaches
  `Authorization: Bearer` to our APIs and does one silent refresh-and-retry on a
  `401`; a route guard carries a `returnUrl`. The token lives in localStorage — a
  documented trade-off vs httpOnly cookies.
- **Same-origin, no CORS** — the SPA calls relative `/auth-api` and `/audit-api`
  paths; nginx proxies them in the container and the dev-server proxy mirrors that
  under `ng serve`, so the browser only ever talks to one origin.
- Shipped as a static bundle by nginx (immutable cache headers, SPA fallback);
  GA4 page views and Sentry errors fire only when their IDs/DSNs are configured.

## 🔄 Core runtime flows

### 🔐 Authentication and authorization

The SPA calls Auth through nginx's same-origin `/auth-api` proxy. Auth supports
the demo login and Google OAuth2, issues RSA-signed access JWTs, and exposes a
JWKS endpoint. Audit retrieves the public JWKS to verify tokens locally; it
never receives Auth's private signing key and does not call Auth for each API
request.

Refresh tokens are single-use and stored with a TTL in Redis. The browser keeps
the access and refresh tokens locally; its HTTP interceptor attaches the bearer
token and performs one refresh-and-retry after a `401`.

### 📝 Event-driven audit trail

Auth publishes login, refresh, and logout `AuditEvent`s asynchronously to the
`audit.events` Kafka topic. Audit also publishes its own chat, RAG, and MCP
activity to that same topic. The Audit consumer processes messages
transactionally, deduplicates at-least-once delivery by `eventId`, and persists
audit rows in PostgreSQL. A slow broker does not block the original feature or
authentication request; missed events during an outage are an accepted
fire-and-forget trade-off.

### 🤖 Assistant, RAG, and MCP

The UI sends chat requests only to Audit, keeping provider credentials outside
the browser. Audit screens prompts for credentials and PII before any external
call. It embeds a question with Voyage, searches pgvector for repository
chunks, builds a role-scoped prompt, and submits only readable retrieved text to
Claude. Vectors never reach Claude.

At startup, Audit incrementally indexes bundled documentation and source code:
new or changed chunks are embedded with Voyage and stored in pgvector. The same
RAG service powers `/mcp`; it exposes repository knowledge only and does not
expose audit rows.

## 🧩 Component map

| Area | Responsibility | Main location |
|---|---|---|
| UI | Angular routes, auth state, dashboard, chat, GA4 and Sentry | `UI/src/app/` |
| Edge | TLS, SPA hosting, same-origin proxying, production Grafana route | `Backend/deploy/Caddyfile`, `UI/nginx.conf` |
| Auth | OAuth/demo login, JWT/JWKS, refresh token lifecycle, event producer | `Backend/Auth/` |
| Audit | Audit APIs, Kafka consumer, RAG, chat, MCP | `Backend/Audit/` |
| Shared library | `AuditEvent` contract and per-instance request limiter | `Backend/common/` |
| Data | Audit records, RAG vectors, refresh tokens, event log | PostgreSQL/pgvector, Redis, Kafka |
| Observability | Metrics, logs, traces, dashboards | `Backend/monitoring/` |

## ⚖️ State, scaling, and resilience

| Component | State model |
|---|---|
| UI/nginx, Auth, Audit | Stateless compute: services can be replicated |
| Redis | Shared single-use refresh-token state |
| Kafka/Redpanda | Durable audit-event log and dead-letter flow |
| PostgreSQL + pgvector | Durable audit records and vector index |
| Prometheus, Loki, Tempo | Persistent telemetry stores |
| Grafana | Config-as-code dashboards and datasources; anonymous read-only viewing |
| RAG index | Stateful but reconstructible from the corpus bundled into the Audit image |

The `common` module's newest-wins limiter is a deliberate exception: it tracks
the active Java worker thread for each `userId + method + path` key, so it must
stay local to each service instance. It is request deduplication, not a shared
rate counter; a superseded request is interrupted and returns `429` with
`Retry-After`. Audit mutations use a transactional checkpoint so an interrupted,
superseded request rolls back rather than leaving partial writes.

## 🗄️ Data, schema & profiles

The backing stores and their state models are in the table above; two design points
that aren't obvious from it:

- **Liquibase owns the schema** — a single dialect-neutral changelog runs unmodified
  on both H2 and Postgres (`ddl-auto=none`), so the schema is a versioned, reviewable
  artifact and the H2/Postgres split can't drift. pgvector is the vector store in
  Postgres environments; an in-memory store stands in on H2.
- **The database is chosen by Spring profile, not by where the service runs** — only
  `LOCAL` hardcodes H2; every other profile takes its datasource from the environment
  (12-factor), so the same image runs on Postgres wherever it's wired up:

| Profile | Database | Used by |
|---|---|---|
| `LOCAL` | H2 in-memory (hardcoded) | bare `gradlew bootRun`; the test suite |
| `DEV` | Postgres (from env, H2 fallback) | the local Docker Compose stack; a shared dev server |
| `SIT` / `UAT` / `PROD` | Postgres (from env) | pre-prod and production |

So `docker compose up` runs the services under `DEV` against the real pgvector Postgres
container — not `LOCAL`/H2 (which would ignore the container). Tests stay on H2 by design
(fast, offline — see the ADRs); the E2E job and prod exercise Postgres.

## 📊 Observability and external reporting

- Prometheus polls the Spring services' metrics endpoints.
- Services send structured logs to Loki and OpenTelemetry traces to Tempo.
- Grafana reads all three sources and is served at `/grafana` in production.
- The Angular SPA reports route-level page views to GA4 only when a measurement
  ID is configured.
- UI, Auth, and Audit send errors to Sentry only when their DSNs are configured;
  performance tracing remains with Tempo.

## 🚦 Continuous integration and delivery

Everything below runs in GitHub Actions — enforced in CI rather than via local
git hooks — so every change is gated the same way no matter who pushes it.

### ✅ Quality gates (on every pull request)

- 🧹 **Lint** — repo-wide hygiene, 🔑 secret scanning (gitleaks +
  `detect-private-key`), and commit-message linting, all reusing
  `.pre-commit-config.yaml` as the single source of truth.
- ☕ **Backend CI** — Spotless formatting, JUnit tests with a JaCoCo **90%
  coverage gate**, `diff-cover` on changed lines, a 🏋️ k6 load test, and 🛡️
  Trivy CVE scans of both the built jars and the container images (results
  uploaded as SARIF to the Security tab).
- 🅰️ **Frontend CI** — ESLint, Prettier, Angular unit tests, and a production
  build.
- 🔎 **CodeQL** — static application security testing (SAST) across the code.
- 📜 **API contract** — regenerates each service's OpenAPI spec from the running
  code and fails on breaking changes via `openapi-diff` (additive changes pass;
  an approved break is opt-in by label).
- 🎭 **E2E** — Playwright drives the full compose stack end to end
  (browser → nginx → Auth → Kafka → Audit → PostgreSQL), nothing mocked.
- 🧬 **Mutation testing** — PIT runs report-only to surface weak assertions that
  line coverage alone would miss.

### 🚀 Delivery (on merge to `main`)

- 📦 **CD** — builds each deployable image once and pushes to GitHub Container
  Registry with three tags (SemVer `0.1.<run>`, `sha-<short>`, and `latest`),
  generates an 📋 SBOM with syft, and ✍️ signs each image **digest** keylessly
  with cosign (GitHub OIDC identity + the public Rekor transparency log — no
  signing key to store or rotate).
- ☁️ **Deploy** — ships the compose stack to the production GCE VM, authenticating
  keylessly through Workload Identity Federation (no exported cloud credentials
  anywhere), then smoke-checks the live origin.

## 🖥️ Run locally

```bash
cd Backend
docker compose up --build
```

This starts the SPA at `http://localhost:4200`, Auth on port `8085`, Audit on
port `8083`, PostgreSQL, Redis, Redpanda, and the observability stack. Chat and
RAG are optional: export `ANTHROPIC_API_KEY` and `VOYAGE_API_KEY` to enable
them; the rest of the system remains usable without either key.

## 📚 Further reading

- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/UI/README.md" target="_blank" rel="noopener noreferrer">Frontend guide</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/README.md" target="_blank" rel="noopener noreferrer">Backend guide</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/docs/adr/README.md" target="_blank" rel="noopener noreferrer">Architecture decisions</a>
- <a href="https://github.com/sahilparekh1212/ask-app/blob/main/Backend/docs/deployment.md" target="_blank" rel="noopener noreferrer">Deployment guide</a>

## 📄 License

**Proprietary — all rights reserved.** This repository is published for
viewing/portfolio evaluation only; no right to use, run, copy, modify, or
distribute the code is granted. See
<a href="https://github.com/sahilparekh1212/ask-app/blob/main/LICENSE" target="_blank" rel="noopener noreferrer">LICENSE</a>.
