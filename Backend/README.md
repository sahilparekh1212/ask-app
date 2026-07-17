# ask-app Backend

_Developer guide to the ask-app backend — what the services do, why they're built this way, and
how to build, run, and operate them._

**Contents:** [LLM chat assistant](#llm-chat-assistant) ·
[RAG MCP server](#rag-mcp-server) ·
[Concepts & design decisions](#concepts--design-decisions--what--how) ·
[Build & test](#build--test) · [Run locally](#run-locally) ·
[Profiles & databases](#profiles--databases) · [Rate limiting](#rate-limiting) ·
[Logging & auditing](#logging-correlation--auditing) · [Observability](#observability) ·
[OpenShift](#deploying-to-openshift) · [Secrets](#secrets) ·
[Security scanning](#security--supply-chain-scanning)

Spring Boot 3.5 / Java 17 multi-module backend (Gradle), deployed live at
https://ask-app.sahilparekh1212.com (GCE VM, deployed from GitHub Actions; OpenShift manifests
also provided).

| Service | Port | Module | Purpose |
|---------|------|--------|---------|
| Auth | 8085 | `Auth` | Google OAuth2 + demo login, issues/refreshes JWTs, JWKS |
| Audit | 8083 | `Audit` | Audit trail (Kafka consumer + query API), LLM assistant, RAG/MCP |

A third subproject, `common`, is a plain shared library: the `AuditEvent` Kafka contract and the
rate limiter. Both services share JWT resource-server security, Swagger (`/swagger-ui.html`),
structured logging, a global exception handler, and actuator health. The non-obvious "why"s live
in [`docs/adr/`](docs/adr/README.md).

### LLM chat assistant

`POST /api/v1/assistant/chat` (Audit) proxies the SPA's chat to Claude, grounded on the repo's
docs/source plus live audit data. Role-aware: every user chats against aggregate stats; only
`ROLE_ADMIN` answers also see recent raw rows. Inbound text is screened server-side (JWT/token
shapes, credential assignments, emails, card-like numbers → refused locally, never forwarded);
auth headers are never proxied ([ADR-0009](docs/adr/0009-llm-chat-assistant-data-flow.md)).
Enable with `ANTHROPIC_API_KEY`; without it the endpoint 503s and nothing else is affected.
Tunables: `ASSISTANT_MODEL` (default `claude-opus-4-8`), `ASSISTANT_MAX_TOKENS`.

### RAG MCP server

The Audit service is also an MCP server: `POST /mcp` exposes semantic search over the repo's own
docs *and bundled source* (pgvector on the stack's Postgres, Voyage embeddings; corpus bundled at
build time, chunked by heading, indexed incrementally by content hash). The synthetic
security-master reference data is indexed alongside — public, non-PII only; audit/user data is
never indexed ([ADR-0010](docs/adr/0010-rag-mcp-server.md)). Tools: `search_knowledge`,
`list_sources`. Enable indexing with `VOYAGE_API_KEY`.

```bash
claude mcp add --transport http ask-app https://ask-app.sahilparekh1212.com/audit-api/mcp
# then, inside Claude Code: "why is there no API gateway?" → grounded in ADR-0005
```

---

## Concepts & design decisions — what & how

| Concept — *what & why* | How — *technology / mechanism* |
|---|---|
| **Statelessness** — any pod serves any request; Auth scales horizontally | JWTs verified locally against Auth's JWKS; the one piece of state (single-use refresh token) is a `RefreshTokenStore` **Strategy** — in-memory for dev, **Redis** (atomic `GETDEL`) when scaled ([ADR-0007](docs/adr/0007-redis-refresh-token-store-for-statelessness.md)). The rate limiter stays *per-pod* on purpose (thread-interrupt dedup, not a counter) |
| **RBAC & token security** — verify identity + role locally, never mint remotely | **Google OAuth2** → **RSA-signed JWT** (verifiers hold only the public key) via **JWKS**; `roles` claim → authorities; `@PreAuthorize` on admin actions ([ADR-0001](docs/adr/README.md)) |
| **Event-driven architecture** — the audit trail is a side effect; producers must not block on the sink | Auth (and Audit's own AI features) publish `AuditEvent`s to Kafka topic `audit.events` `@Async` fire-and-forget; Audit consumes. **Apache Kafka** (single-node KRaft) locally, managed/multi-broker in prod ([ADR-0006](docs/adr/README.md), [ADR-0011](docs/adr/README.md)) |
| **Idempotency** — at-least-once delivery means duplicates happen | Consumer dedups by `eventId` (unique index); retry then dead-letter to `audit.events.DLT` |
| **RDBMS** — filtered/aggregated relational queries want real indexes | **PostgreSQL 16** + Spring Data JPA; one `AuditLogSpecifications` builds *both* search and DB-side `GROUP BY` stats, so rows and counts never disagree; **H2** for tests/LOCAL |
| **Schema as code** | **Liquibase** owns the schema (`ddl-auto=none`); expand/contract changesets keep rolling deploys safe |
| **Rate limiting** — newest request wins, shed gracefully | Newest-wins per `userId+method+path`: superseded request's thread interrupted, its `@Transactional` work rolled back, client gets **429 + `Retry-After`** (see [Rate limiting](#rate-limiting)) |
| **Guarded LLM proxy** — the data flow is the security boundary | Server-side key, inbound secret/PII screening, no auth-header forwarding, role-scoped context in one allowlist class ([ADR-0009](docs/adr/0009-llm-chat-assistant-data-flow.md)) |
| **RAG + vector search** — ground answers in the code that's actually deployed | **pgvector** on the existing Postgres + **Voyage** embeddings; content-hash incremental indexing ([ADR-0010](docs/adr/0010-rag-mcp-server.md)) |
| **MCP server** | Hand-rolled stateless JSON-RPC `/mcp` (`initialize`/`tools/list`/`tools/call`) — the SDK's session/SSE machinery isn't needed |
| **Metrics / logs / traces** | **Micrometer → Prometheus**; structured JSON → **Loki**; OpenTelemetry → **Tempo**, one trace across the async Kafka hop (see [Observability](#observability)) |
| **Domain analytics vs system health** | The event-sourced audit trail feeds the in-app dashboard; Grafana/Prometheus/Loki/Tempo answer the *system* question — two views, deliberately separate |
| **API contract safety** | springdoc emits OpenAPI from running code; an **openapi-diff** PR gate fails only on incompatible changes |
| **Testing depth** | JUnit + **90% JaCoCo gate** per module, diff-cover, **PIT** mutation baseline, **Playwright** E2E vs the compose stack, **k6** load tests |
| **CI / CD / supply chain** | GitHub Actions: build/test/coverage, CodeQL, Trivy, Dependabot, gitleaks, commit lint → versioned GHCR images on merge, **cosign** keyless signing + **syft** SBOM attestations, keyless **WIF** deploy to the VM |
| **Config & secrets** | Profile matrix (LOCAL…PROD) selects behaviour; keys and passwords come from the environment; nothing sensitive committed |

---

## Build & test

JDK 17; use the wrapper (`./gradlew`).

```bash
./gradlew build                # everything (tests + 90% coverage gate)
./gradlew :Audit:test          # one module
./gradlew :Audit:pitest        # mutation testing (report-only)
```

Playwright E2E (`<repo-root>/e2e`) drives the real compose stack — start it first, then
`npm ci && npx playwright test`. CI runs the same suite on every system-affecting PR.

## Run locally

**One command, the whole system:** `docker compose up --build` (root `docker-compose.yml`) —
Postgres, Kafka, both services, observability. See the file's header comment for URLs and the
zero-setup demo login. Skip the build with the GHCR variant (CI-built images, public packages):

```bash
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d --no-build
# pin a build: AI_SANDBOX_TAG=sha-abc1234 docker compose ... up -d --no-build
```

**Or with Gradle**, standalone (default profile LOCAL = in-memory H2, zero setup):

```bash
./gradlew :Auth:bootRun     # first — Audit validates JWTs against its JWKS
./gradlew :Audit:bootRun    # separate terminal; SPRING_PROFILES_ACTIVE=DEV to override
```

**Demo login (no Google setup):** `POST /auth/login` with
`{"username":"demo","password":"demo"}` issues the same JWTs as the Google flow; add
`"role":"ROLE_ADMIN"` to test admin endpoints. Disable with `AUTH_DEMO_ENABLED=false`.

**Google OAuth:** create credentials with redirect URI
`http://localhost:8085/login/oauth2/code/google`, export `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`,
then open `http://localhost:8085/oauth2/authorization/google`. Tokens are returned to
`${FRONTEND_URL}/login/callback` in the URL *fragment* (never query/logs); refresh via
`POST /auth/refresh`.

## Profiles & databases

Which database a service uses is decided by profile, not by where it runs — only `LOCAL`
hardcodes H2; every other profile reads the datasource from the environment (12-factor). The
compose stack runs `DEV` against the real pgvector Postgres; bare `bootRun`/tests use H2
([ADR-0003](docs/adr/0003-h2-over-testcontainers.md)). The Liquibase changelog is
dialect-neutral, so the identical migration runs on both.

| Profile | Datasource | `ddl-auto` | Notes |
|---------|------------|------------|-------|
| LOCAL | In-memory H2 (hardcoded) | update | H2 console at `/h2-console` (`sa`, blank); demo seed on |
| DEV | Env vars, H2 fallback | update | The compose stack; demo seed on |
| SIT / UAT | Env vars | update / validate | No demo seed |
| PROD | Env vars (required) | validate | WARN logging, no seeder/demo endpoints |

Local stack credentials (dev-only, deliberately public): Postgres `localhost:5432`
`audit`/`audit` db `auditdb`; Redis `localhost:6379`, no password. Browser GUIs ship in the
stack — Adminer (`:8082`), Redis Insight (`:5540`), Kafbat Kafka UI (`:8080`) — and are kept out
of prod via a never-activated `local-tools` profile. Real deployments inject their own
credentials (`docker-compose.prod.yml`, `DB_PASSWORD`).

## Rate limiting

Every service enforces **one active request per user per endpoint, newest-wins**: a new request
for the same `userId+method+path` interrupts the older one, rolls back its `@Transactional`
work, and the older client gets **429 + `Retry-After: 30`**. Lock-free `ConcurrentHashMap`
registry, interrupt-based race-safe cancellation, shared via `:common`
(`com.askapp.common.ratelimit`); configurable via `ratelimit.*`.

**Measured (k6):**

| Scenario | Numbers |
|----------|---------|
| Throughput (`search-stats.js`, limiter off, 20 VUs) | 281 req/s sustained, p95 **7.31 ms**, 0% failed |
| Prod smoke (`prod-smoke.js`, live VM over public TLS, 5 VUs) | 12.7 req/s, read p95 **96.7 ms**, 0 × 5xx, 0 × 429 |
| Contention (`rate-limit.js`, 30 VUs on one key) | 715 req/s, **81.5% shed as 429, 0% 5xx** |

The hard gate — a superseded request sheds as `429`, never a `5xx` — held at 0% under load.
Both scripts also run in CI on every PR.

## Logging, correlation & auditing

One structured line per event:
`[<service>] <ts> pod=<host> traceId= spanId= requestId= userId= threadId= url= - <message>`.
Every request carries a correlation UUID (`X-Request-Id` in, MDC, echoed back, in error
payloads). Auditing is two-level: JPA persistence auditing (`created_at`/`updated_at` +
request-UUID "by" columns — never a user identity) and an `AuditInterceptor` emitting
`AUDIT action= resource= status= outcome= durationMs=` lines (no bodies/headers). Filter in
Loki: `{app=~".+-service"} |= "AUDIT"`.

## Observability

The Grafana/Prometheus/Loki/Tempo stack is the **system view** (request rates, p95/p99, logs,
traces); the in-app audit dashboard is the **domain view**. Prod publishes Grafana **read-only**
at **https://ask-app.sahilparekh1212.com/grafana** (anonymous Viewer); Prometheus/Loki/Tempo
stay unpublished. Usage guide with verified example queries:
[docs/observability.md](docs/observability.md).

Telemetry is active in DEV+ only (LOCAL stays console-only). Traces span the async Kafka
boundary: Kafka template/listener observations plus a `ContextPropagatingTaskDecorator` across
the `@Async` hop mean one login is **one trace** — auth's HTTP span → `audit.events send` →
`audit.events receive`. Log lines carry `traceId=`, so Tempo ↔ Loki correlation is
pre-provisioned.

![Grafana ask-app Overview dashboard during a k6 load run](docs/images/grafana-overview-load.png)

Captured from the compose stack with Auth at 2 replicas (Docker-DNS service discovery) under k6
load: request bursts, per-endpoint p95/p99, live logs, and an empty 5xx panel. Standalone local
run: `docker compose -f monitoring/docker-compose.yml up -d`, then bootRun under `DEV` with
`LOKI_URL`/`OTEL_EXPORTER_OTLP_ENDPOINT` pointed at it — Grafana at `:3000` (admin/admin).

## Deploying to OpenShift

> Full commit-to-running-system picture (registry, environments, promotion, rollout/rollback,
> DNS/TLS): [docs/deployment.md](docs/deployment.md). This is the apply sequence.

Manifests under `openshift/<service>/` (Deployment, Service, Route, ConfigMap, HPA, Secret
templates) plus self-hosted `redis`/`postgres`:

```bash
oc apply -f openshift/namespace.yaml
cp openshift/auth/secret.example.yaml openshift/auth/secret.yaml   # fill in; never commit
oc apply -f openshift/auth/secret.yaml -f openshift/postgres/secret.yaml
oc apply -f openshift/redis/ -f openshift/postgres/
oc apply -f openshift/auth/ -f openshift/audit/
oc apply -f openshift/monitoring/prometheus/ -f openshift/monitoring/loki/ -f openshift/monitoring/grafana/
```

Notes: the store images may need a relaxed SCC (`oc adm policy add-scc-to-user anyuid ...`) and
each gets a RWO PVC with `strategy: Recreate`; Postgres is a single-replica SPOF here (managed
instance = the HA route). **Auth at 2 replicas is safe because** every pod shares the Redis
refresh-token store (`AUTH_REFRESH_TOKEN_STORE=redis`) and signs with the same
`AUTH_RSA_PRIVATE_KEY` — miss either and cross-replica refresh/validation silently breaks
([ADR-0007](docs/adr/0007-redis-refresh-token-store-for-statelessness.md)).

## Secrets

Real secrets are never committed — only `*.example.yaml` templates; real files are gitignored,
or create Secrets imperatively (`oc create secret generic ... --from-literal=...`). A
**gitleaks pre-commit hook** guards the repo (`pip install pre-commit && pre-commit install`).
If a secret ever lands in history: **rotate it** — deleting the file doesn't remove it.

## Security & supply-chain scanning

| Layer | Tool | Where |
|-------|------|-------|
| SAST | **CodeQL** | `codeql.yml` (required check) |
| Dependency CVEs | **Trivy** (jar) + **Dependabot** | `backend-ci.yml`; `dependabot.yml` |
| Image CVEs | **Trivy** (image) | `backend-ci.yml` (required check) |
| Secrets | **gitleaks** + detect-private-key | pre-commit + `lint.yml` |

Every image CD pushes is **cosign-signed by digest** (keyless: the signing identity is
`cd.yml`'s OIDC token, logged in Rekor) with a **syft SPDX SBOM** attestation:

```bash
cosign verify \
  --certificate-identity-regexp 'https://github.com/sahilparekh1212/ask-app/\.github/workflows/cd\.yml@.*' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/sahilparekh1212/ask-app/audit:latest
```

The identity pin is the point: a signature only counts if produced by *this repo's* `cd.yml`.
Why GitHub-native + OSS instead of a commercial suite (Snyk et al.): same four categories
covered, results land in the Security tab and gate PRs natively, independent vulnerability
databases layer, zero cost/quota at any scan volume, no vendor lock-in — a commercial platform
earns its place at many-repo scale, not here.
