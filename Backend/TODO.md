# TODO — Interview-readiness punch list (Fullstack Engineer)

Findings

## Fullstack readiness — Angular microfrontend (ACTIVE FOCUS)
What's needed to credibly present this as a Fullstack Engineer project. Backend is in good
shape (Auth: Google OAuth2 → JWT, JWKS, /auth/me, refresh, logout; Audit: paginated/filtered
search + aggregation; rate limiting; observability; CI). The gaps are the UI and the seams it
needs.

### Frontend — the headline deliverable (`UI/` is currently 0 files)
- [ ] **Scaffold the Angular workspace in `UI/`.** Angular 17+ standalone components, routing,
      ESLint + Prettier, unit tests (Jest/Karma). Wire an `npm`/`ng` build.
- [ ] **Microfrontend architecture: shell + remotes.** Use Module Federation
      (`@angular-architects/module-federation`) or Angular Native Federation. Proposed split
      aligned to the backend domains:
      - `shell` (host) — top-level nav, auth/session state, routes to the remotes
      - `auth-mfe` — Google login entry, profile (from `GET /auth/me`), logout, token refresh
      - `audit-mfe` — audit dashboard: server-side paginated/filterable table over
        `/api/v1/audit-logs/search` + charts over `/api/v1/audit-logs/stats`
      - `shared` lib — HTTP auth interceptor, DTO models, design-system components
- [ ] **Auth wiring in the SPA.** HTTP interceptor adding `Authorization: Bearer …`, silent
      refresh via `POST /auth/refresh`, route guards, and a token-storage strategy (see the
      OAuth-handoff item below — it dictates this).
- [ ] **Audit feature UI.** Server-side paginated table (page/size/sort), filter form
      (entityType/action/date range), and stats charts (by action / by entityType) against the
      endpoints already built.
- [ ] **Frontend CI + container.** Add a UI job to GitHub Actions (lint, build, unit tests,
      optional Playwright/Cypress e2e), make it a required check, and containerize the UI
      (nginx) for the compose stack.

### Backend seams the UI depends on (do these before/with the UI)
- [ ] **CORS — hard blocker (elevates the Medium "no CORS" note).** Nothing configures CORS, so
      an Angular SPA on `:4200` cannot call Auth (`:8085`) or Audit (`:8083`). Add a CORS config
      (allowed origins from env) to both services, or front them with a gateway.
- [ ] **OAuth login → SPA handoff.** `OAuth2LoginSuccessHandler` writes the JWT JSON straight to
      the response, so after the Google redirect the browser lands on the Auth server showing raw
      JSON — unusable from an SPA. Redirect back to a UI `/login/callback` route with the tokens
      instead, and decide token transport (URL fragment vs httpOnly cookie).
- [ ] **(Optional) API gateway / BFF.** The UI currently must call two services on two ports.
      A small gateway (or per-service routes + env-driven base URLs) cleans up CORS, auth, and
      the microfrontend's network config — worth articulating even if not built.

### Run-it-in-one-command (reviewer experience)
- [ ] **Top-level `docker-compose`.** Only `Backend/monitoring/docker-compose.yml` exists
      (observability only) and there's no real DB. Add a root compose: Postgres + Auth + Audit +
      UI + Prometheus/Grafana/Loki so a reviewer can `docker compose up` and see the whole system.
- [ ] **DB migrations (Flyway/Liquibase).** PROD runs `ddl-auto=validate` with no migration
      tool, so the schema — including the new audit indexes — is never created outside dev.
      Add Flyway (baseline + index migration) to demo credibly against Postgres.
- [ ] **Demo data seeding.** Seed audit rows on startup (dev-profile `CommandLineRunner` or a
      compose init) so the dashboard isn't empty on first load.

### Portfolio polish
- [ ] **Root `README.md` is one line ("# AI-Sandbox").** Write a real one: one-paragraph pitch,
      architecture diagram (services + UI + observability), UI screenshots/GIF, "how to run"
      (docker compose), tech-stack table, and links to ADRs. First thing a reviewer sees.
- [ ] **Stale module references.** `Backend/Notification/` is now an empty dir not in
      `settings.gradle`; the "Notification doesn't notify" item below and the "duplicated across
      all 3 modules" ratelimit item are out of date (only Audit + Auth build now). Delete the dir
      or rebuild Notification as a real service, and fix those two items.

## High impact
- [x] **Audit/Auth Docker image build fixed.** Dropped the dangling `COPY Survey/build.gradle`
      from both `Audit/Dockerfile` and `Auth/Dockerfile` (Survey was removed from the project),
      which was failing the image build at that step. Not build-verified here (no Docker) — do a
      `docker build -f Audit/Dockerfile .` once to confirm the full multi-stage build.
- [ ] **Enforce PR-only on `main` (no direct pushes).** Branch protection now requires the four CI checks ("Build, test & coverage", "Load test (k6)", "pre-commit (hygiene + secrets)", "Conventional commit messages") to pass before a PR can merge — done via `gh api`. Still open: direct pushes to `main` bypass the gate entirely. Add `required_pull_request_reviews` (count 0 is fine for solo) to force all changes through PRs, and consider `enforce_admins: true` so the rule applies to the owner too.
- [ ] **`UI/` frontend is an empty stub.** No package.json, no source files — "fullstack" is currently unsubstantiated. Either build a minimal real frontend (e.g. React/Next hitting Auth via OAuth2+JWT, and Audit/Notification) or stop billing the project as fullstack.
- [ ] **Business logic is generic CRUD with no real complexity.** Audit logs and notifications are basic create/read/soft-delete. Add real complexity to one module — e.g. notification delivery with retries/backoff, or audit log querying/aggregation with pagination and filters backed by indexes.
- [x] **Kafka event-driven audit logging — implemented.** Auth publishes `AuditEvent`s to `audit.events` on LOGIN/TOKEN_REFRESH/LOGOUT (`AuditEventPublisher`, fire-and-forget `@Async` so auth isn't coupled to broker availability, keyed by `entityType`); Audit consumes via `@KafkaListener` (`AuditEventConsumer`) and persists `AuditLog`s. At-least-once handling is idempotent by `eventId` (unique `idx_audit_event_id` + `existsByEventId`); failures retry then dead-letter to `audit.events.DLT` (`KafkaConsumerConfig`). Local broker: `Backend/kafka/docker-compose.yml` (Redpanda + console). JSON contract decouples the two `AuditEvent` records (no shared module). Unit-tested; CI needs no broker (listener disabled in tests/load-test).
  - [x] **Consume→persist gated in CI.** `AuditEventConsumerIntegrationTest` uses an in-JVM embedded Kafka broker to verify the real path — JSON contract, `@KafkaListener`, JPA persistence, and idempotent dedup by `eventId` — and runs in the normal `test` task (no Docker). A full two-service smoke against the Redpanda compose (Auth → Kafka → Audit, plus the DLT) is still a nice manual check: `docker compose -f Backend/kafka/docker-compose.yml up -d` then run both services with `KAFKA_BOOTSTRAP_SERVERS=localhost:19092`.
  - [ ] **Extract `AuditEvent` into a shared `:common` module** (ties into the duplicated-ratelimit item) instead of one copy per service.
- [ ] **Audit module's "immutability" claim isn't real.** `AuditLog.java` still exposes public setters (`setEntityType`, `setDetails`, `setDeleted`); immutability is only enforced by the absence of a PUT/PATCH route, not by entity design. Lock this down at the entity level (e.g. make fields final / remove setters, use a builder).
- [ ] **Notification module doesn't notify anyone.** It persists a `channel` field but never dispatches anything through it (no email/SMS/push integration or adapter interface). Either implement real dispatch or rename/reframe the module honestly.
- [ ] **Git history reads as AI-bulk-generated, not iterative.** Only ~9 real commits: a couple of giant "dump everything" commits followed by cleanup PRs deleting Account/Transaction/Report/Survey modules, several co-authored by Claude. Going forward, commit in smaller, narratively coherent units that survive "walk me through how you built this."

## Security scanning — implemented (secret scanning + CVE/SAST now in place)
Previously the only backend "vulnerability check" was secret scanning (gitleaks +
detect-private-key in `lint.yml`). Now added:
- [x] **Dependabot** — `.github/dependabot.yml` (gradle + github-actions ecosystems, weekly).
      Raises security alerts and opens update PRs for vulnerable dependencies.
- [x] **CodeQL SAST** — `.github/workflows/codeql.yml` (java-kotlin, manual build) on PR/push
      to `main` + weekly; results land in the Security tab.
- [x] **Trivy CVE scan** — `trivy` job in `backend-ci.yml`: builds the boot jars, scans bundled
      deps, uploads SARIF to the Security tab, and fails on fixable HIGH/CRITICAL CVEs.
- [ ] **Enable Dependabot alerts + security updates in the GitHub UI.** The committed
      `dependabot.yml` only schedules version-update PRs; the vulnerability *alerts* and
      auto security-update PRs are a separate repo toggle: Settings → Code security →
      enable "Dependabot alerts" and "Dependabot security updates". (Not configurable in code.)
- [ ] **Promote `CodeQL` and `Trivy CVE scan` to required branch-protection checks.** Currently
      they run but don't block merges (left non-required so a newly-published CVE doesn't lock all
      merges). After confirming a first green run, add both contexts to the `main` protection rule
      via `gh api` / Settings → Branches.
- [ ] **Extend Trivy to the Docker images.** Today it scans the boot-jar dependencies only; also
      build and scan the `Audit`/`Auth` images to catch base-image/OS CVEs.

## Medium impact

- [ ] **No ADRs or design-tradeoff docs.** README explains "what" well but never "why" — e.g. why `ConcurrentHashMap` over Redis for rate limiting/refresh tokens, why RSA over HMAC for JWT signing, why H2 over Testcontainers for tests. Add a short `docs/adr/` with 3–4 real decisions and alternatives considered.
- [ ] **Auth refresh-token store is in-memory only.** `TokenService.java` explicitly comments it as a placeholder — tokens vanish on restart and don't work across replicas. Same issue applies to the ephemeral RSA keypair generated when no `AUTH_RSA_PRIVATE_KEY` env var is supplied. Replace with Redis/DB-backed storage, or at minimum document this as a known limitation.
- [ ] **Auth refresh flow drops claims on rotation.** `AuthController.refresh()` calls `generateTokens(userId, null, null)`, losing email/name on refresh — minor logic bug worth fixing alongside the token tests.
- [ ] **Observability is configured but unproven end-to-end.** Prometheus/Grafana/Loki wiring is real (working PromQL/LogQL panels, not a template) but there's no evidence it was run against real traffic. Capture a dashboard screenshot from an actual load test and include it in the README.
- [x] **Per-endpoint latency (p95/p99) now visible.** Enabled `management.metrics.distribution.percentiles-histogram.http.server.requests=true` in both services (emits `http_server_requests_seconds_bucket`) and added a "Latency p95/p99 by endpoint" panel to `monitoring/grafana/dashboards/aisandbox-overview.json` (`histogram_quantile` over the buckets). Response time was already partly observable (avg+max via Micrometer, `durationMs` in `AuditInterceptor` logs, k6 p95 at load). Still worth capturing a screenshot from a real load run (ties to the observability item above).
- [ ] **OpenShift manifests use `emptyDir`, no PVCs.** README already flags this, but if the project is framed as "production-ready" elsewhere this undercuts credibility — either add a PVC variant or state the limitation explicitly.
- [ ] **Minor security smells in Auth:** no `@Valid`/`@NotBlank` on `RefreshRequest`; no CORS policy configured anywhere; `MdcLoggingFilter.extractSubFromJwt()` does a naive unverified base64 substring search for logging purposes (not an auth bypass, but fragile/non-idiomatic — should use a JSON parser at minimum).

## Low impact

- [ ] **Duplicated `ratelimit/` package across all 3 modules.** Byte-for-byte identical code in Audit/Auth/Notification with no shared `:common` Gradle module — extract it. Also won't scale past ~6–8 services without a shared platform/BOM module; worth being able to articulate this in interviews even if not built.
- [ ] **Rate limiter's "high concurrency" claim — capture the numbers in the README.** k6 scripts now exist and run in CI: `Backend/load-test/search-stats.js` (throughput/latency on search+stats, limiter off) and `Backend/load-test/rate-limit.js` (asserts the limiter sheds concurrent same-key load as 429, never 5xx). Remaining: pull the k6 summary (req/s, p95, 429 rate) from a CI run into the README so the claim is backed by published results. Same load traffic can feed the "observability unproven end-to-end" item (capture a Grafana panel during the run).
