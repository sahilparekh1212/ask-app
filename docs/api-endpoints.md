# API endpoints & who can reach them

Every HTTP endpoint the two Spring Boot services expose, with the access rule that applies
**directly** (network + authentication/authorization) and the path by which each is reached
**indirectly** (through the SPA, an OAuth redirect, an MCP client, the scraper, etc.).

Two services are deployable: **Auth** (`com.askapp.auth`, port 8085) and **Audit**
(`com.askapp.audit`, port 8083). `common` is a shared library, and the UI is Angular — neither
exposes endpoints of its own.

## Topology — who sits in front

```
Internet ──HTTPS──▶ Caddy ($DOMAIN)
                     ├─ /grafana*  ──▶ grafana:3000   (anonymous read-only Viewer)
                     └─ everything ──▶ ui:80 (nginx)
                                        ├─ /auth-api/*  ─▶ auth:8085/*   (prefix stripped)
                                        ├─ /audit-api/* ─▶ audit:8083/*  (prefix stripped)
                                        └─ /*           ─▶ SPA static files
```

The edge proxies forward the **whole** prefix, so every path on a service — including its
unauthenticated (`permitAll`) paths — is reachable from the public origin at `/auth-api/…` or
`/audit-api/…`. **Spring Security is the real gate, not the network.** Prometheus/Loki/Tempo stay
unpublished (SSH tunnel only); the in-cluster scraper and OpenShift probes hit the pods directly.

Sources: [`UI/nginx.conf`](../UI/nginx.conf), [`Backend/deploy/Caddyfile`](../Backend/deploy/Caddyfile).

## Access levels

| Level | Meaning |
|---|---|
| **Public** | `permitAll` — no JWT required; reachable by anyone who can reach the port (including via the public proxy) |
| **Authenticated** | A valid access token is required; any role (default demo role is `ROLE_USER`) |
| **ADMIN** | `@PreAuthorize("hasRole('ADMIN')")` — a token carrying `ROLE_ADMIN` |

## Auth service — `com.askapp.auth`, port 8085, public prefix `/auth-api`

Resource server: everything is `authenticated()` **except** the `permitAll` list in
[`SecurityConfig`](../Backend/Auth/src/main/java/com/askapp/auth/config/SecurityConfig.java).

| Method & path | Access | Reached directly by / indirectly via |
|---|---|---|
| `POST /auth/login` | Public (gated by `auth.demo.enabled`, default on) | SPA demo-login form; anyone. Body may request `role:ROLE_ADMIN` |
| `POST /auth/refresh` | Public | SPA silent token refresh |
| `POST /auth/logout` | Public | SPA logout (revokes the refresh token in the body) |
| `GET /auth/me` (+ `X-API-Version:2` variant) | Authenticated | SPA "current user" |
| `GET /.well-known/jwks.json` | Public | **Audit service** (fetches keys to validate JWTs); any resource server |
| `GET /oauth2/authorization/google` | Public (Spring OAuth2 client) | Browser starting Google sign-in |
| `GET /login/oauth2/code/google` | Public (OAuth callback) | Google redirect → browser |
| `GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Public | Anyone (also via public proxy) |
| `GET /actuator/health`, `/actuator/prometheus` | Public | OpenShift probes; Prometheus; anyone via `/auth-api/actuator/**` |

## Audit service — `com.askapp.audit`, port 8083, public prefix `/audit-api`

Resource server + method security. `permitAll` = swagger, api-docs, actuator, `/mcp`; everything
else `authenticated()`; two endpoints add `@PreAuthorize("hasRole('ADMIN')")`. See
[`SecurityConfig`](../Backend/Audit/src/main/java/com/askapp/audit/config/SecurityConfig.java).

| Method & path | Access | Reached directly by / indirectly via |
|---|---|---|
| `GET /api/v1/audit-logs` | Authenticated | SPA dashboard (v1 flat list) |
| `GET /api/v1/audit-logs/search` | Authenticated | SPA search (paginated, filtered) |
| `GET /api/v1/audit-logs/stats` | Authenticated | SPA charts (aggregate counts) |
| `GET /api/v1/audit-logs/stats/timeline` | Authenticated | SPA timeline (hour/day buckets) |
| `GET /api/v1/audit-logs/{id}` | Authenticated | SPA detail |
| `POST /api/v1/audit-logs` | Authenticated (any role can create) | SPA; services posting audit rows |
| `DELETE /api/v1/audit-logs/{id}` | **ADMIN** | SPA admin (soft-delete) |
| `GET /api/v1/audit-logs/health` | Authenticated | plain-string service health |
| `GET /api/v2/audit-logs` | Authenticated | SPA (paginated v2 listing) |
| `POST /api/v1/refdata/ingest` | **ADMIN** | SPA admin / ops (bulk security-master load) |
| `GET /api/v1/refdata/securities` | Authenticated | SPA reference-data browser |
| `GET /api/v1/refdata/securities/{instrumentId}` | Authenticated | SPA |
| `GET /api/v1/meta/features` | Authenticated | SPA (feature flags for the UI) |
| `POST /api/v1/assistant/chat` | Authenticated (ADMIN gets audit-row grounding; USER aggregates only) | SPA chat → server-side Claude proxy |
| `POST /api/v1/audit-logs/demo` | Authenticated — **LOCAL/DEV only** (404 in SIT/UAT/PROD) | SPA "Add demo logs" button |
| `GET /mcp` | Public → `405` by design | MCP clients probing for a stream |
| `POST /mcp` | Public (unauthenticated by design — ADR-0010) | MCP clients: `initialize`, `ping`, `tools/list`, `tools/call` → `search_knowledge`, `list_sources` |
| `GET /swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | Public | Anyone (also via public proxy) |
| `GET /actuator/health`, `/actuator/prometheus` | Public | OpenShift probes; Prometheus; anyone via `/audit-api/actuator/**` |

## Access summary by caller

- **Anonymous internet user** — reaches every `permitAll` path through the public edge: Auth
  login/refresh/logout, JWKS, OAuth entry points, both services' Swagger + `/v3/api-docs`, both
  services' `/actuator/health` + `/actuator/prometheus`, and Audit's `/mcp`.
- **Authenticated USER** (default demo login, or a Google sign-in) — all the "Authenticated" rows;
  on `/assistant/chat` gets aggregate-only grounding; cannot delete logs or ingest refdata.
- **Authenticated ADMIN** — everything USER can, plus `DELETE audit-logs/{id}`, `refdata/ingest`,
  and audit-row-grounded assistant answers.
- **Service-to-service** — Audit pulls Auth's JWKS to validate tokens (the only cross-service HTTP call).
- **Infra** — OpenShift probes hit `/actuator/health`; Prometheus scrapes `/actuator/prometheus`
  (pod-direct, not via the proxy).
- **k6 load generator** — under `SPRING_PROFILES_ACTIVE=…,LOADTEST` (CI only),
  [`LoadTestSecurityConfig`](../Backend/Audit/src/main/java/com/askapp/audit/config/LoadTestSecurityConfig.java)
  makes **every** Audit endpoint `permitAll`, so the job hits the real routes without JWTs. Never
  set in DEV/SIT/UAT/PROD.

## Security notes

1. **Demo login can self-issue an ADMIN token.** `POST /auth/login` is public and honors
   `role:"ROLE_ADMIN"` in the body (default `auth.demo.enabled=true`). While demo login is on, the
   ADMIN-gated endpoints are reachable by any caller who first mints an admin token — intentional
   for a recruiter demo. Set `auth.demo.enabled=false` to close it.
2. **Actuator and Swagger are publicly reachable through the proxy**, not just from in-cluster
   infra — the edge forwards the whole prefix and those paths are `permitAll`. Exposure is limited
   to `health,prometheus` (no `env`/`beans`/etc.), so it is metrics + up/down, not config, but it
   is internet-visible at `/{auth,audit}-api/actuator/…` and `/{…}/swagger-ui.html`.
3. **`/mcp` is intentionally unauthenticated** (ADR-0010): the RAG corpus is this repo's own public
   docs and synthetic reference data — no audit rows, no user data — so retrieval has nothing the
   JWT roles protect.
