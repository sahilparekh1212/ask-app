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
- [x] **CORS — implemented.** Both services now configure CORS in `SecurityConfig` via
      `CORS_ALLOWED_ORIGINS` (default `http://localhost:4200`), so an Angular SPA on `:4200` can
      call Auth (`:8085`) and Audit (`:8083`) directly (see the "Minor security smells" item under
      High impact for the implementation details).
- [x] **OAuth login → SPA handoff — implemented.** `OAuth2LoginSuccessHandler` now
      `response.sendRedirect()`s to `${FRONTEND_URL}/login/callback` (default
      `http://localhost:4200`, configurable via `app.frontend-url`) with the tokens in the URL
      *fragment* (`#access_token=...&refresh_token=...&expires_in=...&token_type=Bearer`), not
      raw JSON on the Auth server's own origin. Chose the fragment over an httpOnly cookie: the
      rest of the API already expects the client to attach `Authorization: Bearer` itself (see
      Swagger's Authorize button, `/auth/refresh`'s JSON body), and fragments are never sent to a
      server or logged (unlike a query string), so this doesn't require redesigning that model
      just to land tokens after OAuth specifically. Values are `URLEncoder`-encoded so `+`/`/`/`=`
      in a token can't corrupt the fragment's `key=value` parsing — a real risk that would've
      otherwise only shown up once real JWTs with those characters got issued. README's login-flow
      docs updated with the exact redirect shape and a reminder for the (still-unbuilt) UI route to
      call `history.replaceState` after reading `window.location.hash` so tokens don't linger in
      browser history. `ObjectMapper` dependency dropped from the handler (no longer serializes
      JSON to the client).
- [x] **(Optional) API gateway / BFF — articulated, not built.** This item explicitly said
      "worth articulating even if not built," so wrote
      [ADR-0005](docs/adr/0005-no-api-gateway-yet.md) instead of building one: why direct-call
      + CORS beats a gateway with exactly two services and no cross-service composition need,
      and the three concrete triggers that would flip that calculus (a third service, cross-
      cutting concerns that don't belong per-service, or the microfrontend split actually landing).
      Also names that the duplication half of what a gateway would centralize is already solved
      differently — via the `:common` module, not a network hop.

### Run-it-in-one-command (reviewer experience)
- [x] **Top-level `docker-compose` — added.** `Backend/docker-compose.yml`: Postgres, Redpanda
      (+ console), Prometheus/Loki/Grafana, and both services, so `docker compose up --build`
      brings up the whole backend in one command. No UI service yet — none exists to add (see the
      `UI/` items). Inlines the existing `kafka/`- and `monitoring/`-scoped compose files' service
      definitions rather than composing them, so it's one predictable file instead of a merge; both
      narrower files still work standalone for a smaller local loop. Had to add
      `runtimeOnly 'org.postgresql:postgresql'` to `Audit/build.gradle` — the driver genuinely
      didn't exist yet (README's own "External database" section documented adding it as a
      manual step; a compose file claiming "Postgres" without it would've been broken on first
      use). This is also the first real exercise of the "run it against a real Postgres" follow-up
      noted on the Liquibase item. Not run end-to-end here (no Docker locally) — verified instead
      with `docker compose config` (validates and fully renders the spec: build contexts, env
      interpolation, volume bind-mount paths, and the depends_on health-check graph all resolve
      correctly without the daemon running) and `python -m yaml` parsing.
- [x] **DB migrations with Liquibase — implemented (Audit).** Added `liquibase-core` and a
      baseline changelog at the default path (`db/changelog/db.changelog-master.yaml`) that
      creates the `audit_logs` table + all three indexes (incl. the unique `idx_audit_event_id`);
      Hibernate now runs `ddl-auto=none` in every profile so Liquibase owns the schema (previously
      PROD `validate` had no schema to validate against). Verified on H2 via the `@DataJpaTest` /
      embedded-Kafka tests and a running-app smoke (insert works with `ddl-auto=none`). Auth has no
      DB, so N/A there. Follow-up: run it against a real Postgres (DEV/PROD) once one is wired.
- [x] **Demo data seeding — implemented.** `DemoDataSeeder` (`@Profile({"LOCAL","DEV"})`,
      `CommandLineRunner`) seeds 15 realistic rows across 5 `entityType`s and 5 `action`s on
      startup, skipping if the table already has rows (idempotent across restarts against a
      persistent DEV DB) and disableable via `demo.data.seed.enabled=false`. So `/api/v1/audit-logs`,
      `/search`, and `/stats` all have real data on first run — the future audit dashboard UI
      (and a reviewer poking at Swagger) won't hit an empty table.

### Portfolio polish
- [x] **Root `README.md` — written.** Was literally one line (`# AI-Sandbox`) at the actual repo
      root (`/README.md`, one level above `Backend/` — `Backend/README.md` is the detailed one and
      was already substantial from earlier items). Added: one-paragraph pitch, a Mermaid
      architecture diagram (client → Auth/Audit → Kafka → Postgres, plus the observability fan-out
      — GitHub renders Mermaid natively, no image asset to keep in sync), a tech-stack table, "run
      it" (`docker compose up --build` + the zero-setup demo-login curl example), and links to
      `Backend/README.md`, the ADRs, and the TODO itself. Deliberately skipped UI screenshots/GIF —
      there's no UI to screenshot yet (see the `UI/` items), and a placeholder image would be
      exactly the kind of overclaiming this whole TODO sweep has been fixing. The "Status" callout
      says that plainly instead. Verified every relative link in the new file resolves to a real
      path in the repo.
- [x] **Stale module references — cleaned up.** Deleted the empty, untracked `Backend/Notification/`
      dir (it was never in `settings.gradle` or in git — only build output and empty source trees).
      Only Audit + Auth build now; the "Notification doesn't notify" and "duplicated ratelimit"
      items below have been reworded to match.

## High impact
- [x] **Audit/Auth Docker image build fixed.** Dropped the dangling `COPY Survey/build.gradle`
      from both `Audit/Dockerfile` and `Auth/Dockerfile` (Survey was removed from the project),
      which was failing the image build at that step. Not build-verified here (no Docker) — do a
      `docker build -f Audit/Dockerfile .` once to confirm the full multi-stage build.
- [x] **Enforce PR-only on `main` (no direct pushes) — implemented.** Added `required_pull_request_reviews` (0 approvals, solo repo) and `enforce_admins: true` via `gh api` on top of the existing four required CI checks. Direct pushes to `main`, including from the owner, are now rejected; all changes must go through a PR.
- [x] **RBAC (admin & user roles) — implemented.** Auth mints a `roles` claim (a single-element
      array, e.g. `["ROLE_ADMIN"]`) on every JWT: `TokenService.generateTokens(...)` takes a
      `role` param, threaded through the demo login, Google OAuth login (always `ROLE_USER` — see
      `OAuth2LoginSuccessHandler`), and refresh (preserved via `RefreshClaims`, same pattern as the
      email/name fix). The demo login accepts an optional `"role":"ROLE_ADMIN"` in the request body
      (validated with `@Pattern`, defaults to `ROLE_USER`) so admin-gated endpoints are testable
      without a real IdP modeling roles. Audit's `SecurityConfig` gained a `JwtAuthenticationConverter`
      mapping the `roles` claim onto Spring Security authorities plus `@EnableMethodSecurity`, and
      `AuditLogController.delete()` is now `@PreAuthorize("hasRole('ADMIN')")` — the one admin-only
      action called out in this item. Verified end-to-end with a MockMvc test asserting `ROLE_USER`
      gets 403 and `ROLE_ADMIN` gets 204 (`AuditLogControllerSecurityTest`) — plain unit tests that
      call the controller method directly can't exercise `@PreAuthorize`, since it only fires behind
      a real Spring AOP proxy. That test surfaced a real bug along the way: `AccessDeniedException`
      was falling into the catch-all exception handler and returning 500 instead of 403 — fixed with
      a dedicated handler in `GlobalExceptionHandler` (same class of bug as the validation-exception
      fix from the "Minor security smells" item).
- [x] **`UI/` frontend is an empty stub — resolved via the "stop billing it as fullstack" option.**
      `UI/` is still empty (that half of the item is unchanged and stays tracked by the Angular
      items above it) — but the "stop billing the project as fullstack" resolution is now real,
      not just a checkbox: the root README's pitch calls this "production-shaped backend
      engineering," never "fullstack," and its own **Status** line says plainly "the backend is
      real and hardened... the Angular UI in `UI/` hasn't been built yet." Repo-wide search
      confirms zero remaining "fullstack" claims in either README (root or `Backend/`) — the only
      places "fullstack"/"Fullstack" still appear are `TODO.md`'s own internal punch-list framing
      (an engineering backlog, not reviewer-facing marketing copy) and the Angular items still
      above this one, which is exactly right: the ambition is tracked honestly, not oversold.
- [x] **Business logic complexity — already satisfied by audit querying/aggregation (this item
      predates that work being credited).** The TODO's own "or" clause names exactly what
      `AuditLogService` already does: `search()` is dynamically filtered (`AuditLogSpecifications`,
      built from `AuditLogFilter`'s entityType/action/date-range), paginated, and sort-whitelisted
      (`SORTABLE` set — an unindexed/arbitrary `sort=` param is dropped rather than 500ing or table
      -scanning); `aggregate()` runs a database-side `GROUP BY` (via `CriteriaBuilder`, not an
      in-memory `groupingBy`) reusing the *identical* `Specification` as `search()` so the counts
      and the rows always agree on what "matching" means — and both ride the same three indexes
      documented on `AuditLog` (`idx_audit_active_created`, `idx_audit_entity_type_action`,
      `idx_audit_event_id`). Notification-delivery-with-retries was the other named option but the
      module was removed (see "No Notification service" below) — not needed since this option
      already closes the item.
- [x] **Kafka event-driven audit logging — implemented.** Auth publishes `AuditEvent`s to `audit.events` on LOGIN/TOKEN_REFRESH/LOGOUT (`AuditEventPublisher`, fire-and-forget `@Async` so auth isn't coupled to broker availability, keyed by `entityType`); Audit consumes via `@KafkaListener` (`AuditEventConsumer`) and persists `AuditLog`s. At-least-once handling is idempotent by `eventId` (unique `idx_audit_event_id` + `existsByEventId`); failures retry then dead-letter to `audit.events.DLT` (`KafkaConsumerConfig`). Local broker: `Backend/kafka/docker-compose.yml` (Redpanda + console). JSON contract decouples the two `AuditEvent` records (no shared module). Unit-tested; CI needs no broker (listener disabled in tests/load-test).
  - [x] **Consume→persist gated in CI.** `AuditEventConsumerIntegrationTest` uses an in-JVM embedded Kafka broker to verify the real path — JSON contract, `@KafkaListener`, JPA persistence, and idempotent dedup by `eventId` — and runs in the normal `test` task (no Docker). A full two-service smoke against the Redpanda compose (Auth → Kafka → Audit, plus the DLT) is still a nice manual check: `docker compose -f Backend/kafka/docker-compose.yml up -d` then run both services with `KAFKA_BOOTSTRAP_SERVERS=localhost:19092`.
  - [x] **`AuditEvent` extracted into a shared `:common` module — done alongside the ratelimit
        extraction below.** See that item for the full writeup; the short version for the Kafka
        contract specifically: `AuditEvent` now lives once at `com.aisandbox.common.event`, both
        services' Kafka JSON type-mapping properties (`spring.json.value.default.type` /
        `trusted.packages`) point at it, and the embedded-Kafka `AuditEventConsumerIntegrationTest`
        exercises real (de)serialization through the new package end-to-end.
  - [x] **Transactional outbox — took the "accept the tradeoff and document it" option.** Wrote
        [ADR-0006](docs/adr/0006-fire-and-forget-audit-events.md): why fire-and-forget stays (the
        outbox pattern needs a transactional store to write the event into *atomically with the
        source action*, and Auth deliberately has no datastore — building one just to back this
        would be a bigger architectural change than the audit item alone justifies, and should be
        a deliberate call tied to the statelessness item, not a side effect here), what's actually
        accepted (at-most-once delivery specifically on broker outage — the action itself still
        succeeds, only its audit row is lost), and why the alternative of making the publish
        synchronous is explicitly worse (a Kafka outage should never block login). Also names the
        real trigger to revisit: if Auth gets a datastore for any other reason, outbox becomes the
        natural next step, not a separate project.
- [x] **Audit module's "immutability" claim — now real.** Removed every setter from `AuditLog.java`. Business fields (`entityType`/`action`/`details`/`eventId`) are set only via constructors (a `@JsonCreator`-annotated 3-arg one for REST-created rows, a 4-arg one carrying `eventId` for `AuditEventConsumer`-created rows); `setDeleted(boolean)` was replaced with a one-way `markDeleted()` so soft-delete is the only post-creation mutation the entity allows.
- [x] **No Notification service — ambition dropped, verified clean.** This item offered two
      resolutions: build it fresh, or drop the ambition from the README. Went with dropping it —
      a from-scratch async-dispatch service is a real scope decision, not a TODO-sweep item.
      Verified there's nothing left to walk back: repo-wide search (`.md`/`.java`/`.yml`/
      `.properties`, excluding build output) turns up zero "notification" mentions anywhere —
      README, ADRs, and code are all already honest that this doesn't exist.
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
- [x] **Dependabot alerts + security updates — enabled.** Turned on via `gh api PUT
      repos/.../vulnerability-alerts` and `.../automated-security-fixes` (repo-setting toggles,
      not configurable in `dependabot.yml`). Vulnerability alerts now raise and auto
      security-update PRs open automatically, on top of the existing weekly version-update PRs.
- [x] **`CodeQL` and `Trivy CVE scan` promoted to required branch-protection checks.** Both had a
      confirmed green run on `main` ("Analyze (java-kotlin)" and "Trivy CVE scan" job names); added
      as required contexts via `gh api` alongside the existing four. A CVE finding or SAST alert on
      a PR now blocks merge instead of just landing in the Security tab.
- [x] **Trivy extended to the Docker images — report-only pending a confirmed green run.** The
      `trivy` job in `backend-ci.yml` now also `docker build`s both `Audit/Dockerfile` and
      `Auth/Dockerfile` and scans each resulting image (not just the boot-jar dependencies), so
      base-image/OS CVEs (e.g. in `eclipse-temurin:17-jre-alpine`) are caught too. Uploaded to the
      Security tab under distinct SARIF categories (`trivy-audit-image`/`trivy-auth-image`, plus
      `trivy-jars` on the existing jar scan, so the three uploads in one job don't overwrite each
      other). Deliberately **not** wired to `exit-code: '1'` yet — this job is already a required
      branch-protection check, and unlike the jar scan (already confirmed green), the image scan
      has never run, so it could turn up an existing base-image CVE and lock out every PR on day
      one. Not build-verified here (no Docker locally, same as the earlier Dockerfile fix) — once a
      run on `main` is confirmed green, add `exit-code: '1'` to both new scan steps to make it a
      real gate like the jar scan.

## Medium impact

- [x] **ADRs / design-tradeoff docs — added.** New `docs/adr/` with four real decisions and
      alternatives considered: RSA over HMAC for JWT signing, in-memory over Redis for rate
      limiting/refresh tokens (with the multi-replica consequence spelled out), H2 over
      Testcontainers for tests, and Liquibase over Hibernate `ddl-auto` for schema ownership.
      Linked from the root README.
- [x] **Auth refresh-token store is in-memory only — documented as a known limitation (not fixed).**
      This item explicitly allows either replacing the store or documenting the limitation; went
      with documentation, since standing up Redis is a real infra decision that shouldn't happen
      as a drive-by TODO sweep. [`docs/adr/0002`](docs/adr/0002-in-memory-rate-limit-and-refresh-store.md)
      covers why `ConcurrentHashMap` was chosen, what breaks past one replica (both the refresh-token
      store and the rate limiter — the *effective* rate limit becomes replicas × configured limit,
      not the configured limit), and that nothing survives a restart. Also fixed a stale claim in
      that same ADR while touching it: it previously said Redis would give refresh-token consumption
      "a real atomic compare-and-delete instead of the current `ConcurrentHashMap.remove()`" — that
      was written before the "Handle concurrency explicitly" item proved `remove()` is already
      atomic per key, so it's now corrected to say Redis is needed for *cross-replica* visibility,
      not to fix a single-process race that doesn't exist. The ephemeral-RSA-keypair half of this
      item is covered by the same ADR (ADR-0001) and the existing code comment in `JwtConfig`.
- [ ] **Make services stateless, remove single points of failure, then handle load & scale.** HPA manifests exist (`openshift/audit/hpa.yaml`, `openshift/auth/hpa.yaml`), but scaling past one replica is unsafe today: the refresh-token store and rate limiter are in-memory `ConcurrentHashMap`s and Auth's RSA signing key is ephemeral per-pod (see the in-memory-token-store item), so a second replica breaks refresh + JWT validation. There are also SPOFs — single-replica broker (Redpanda dev-container), single DB, in-memory state. Work: (1) **stateless** — externalize state to Redis (shared token store, rate-limit counters, managed signing key); (2) **no SPOF** — run ≥2 replicas of each service + a multi-broker Kafka + DB HA/replica; (3) **load & scale** — tune HPA (CPU/mem, min/max replicas), size the DB layer (HikariCP pool now; read replicas/partitioning later), and prove it with the existing k6 load test.
- [x] **Handle concurrency explicitly — refresh-token race verified, not a bug.** The TODO here
      previously claimed "refresh-token consumption isn't guaranteed single-use under a race" —
      checked, and that's not actually true: `ConcurrentHashMap.remove(key)` is atomic per key, so
      two threads racing `consumeRefreshToken()` on the same token can't both get a hit. Added
      `TokenServiceTest.consumeRefreshToken_isSingleUseUnderConcurrentRacingConsumers` (50 threads,
      one token, asserts exactly one success) to prove it under real load rather than assume it —
      this is exactly the kind of claim that reads right in review and is wrong in practice, so
      it's now backed by a test instead of a comment. The rate limiter (interceptor + transactional
      rollback) and the Kafka consumer (idempotent + single-partition-ordered) were already sound.
      Real remaining gap, unchanged: `AuditLog` entities have no JPA `@Version` optimistic locking
      — currently low-risk since the only post-creation mutation is the idempotent one-way
      `markDeleted()` (see ADR-adjacent note: no concurrent *field* updates exist yet), but add
      `@Version` if/when a real update path is added.
- [x] **Auth refresh flow drops claims on rotation — fixed.** `TokenService` now stores `email`/`name` alongside `userId` in the refresh-token entry and returns them from `consumeRefreshToken()` (as a new `RefreshClaims` record); `AuthController.refresh()` passes them through to `generateTokens()` instead of `null, null`, so a rotated access token keeps its claims.
- [ ] **Observability is configured but unproven end-to-end.** Prometheus/Grafana/Loki wiring is real (working PromQL/LogQL panels, not a template) but there's no evidence it was run against real traffic. Capture a dashboard screenshot from an actual load test and include it in the README.
- [x] **Identify which pod produced a given log line — implemented.** Added `pod=${HOSTNAME:-local}`
      to both services' console and Loki message patterns in `logback-spring.xml` (the Loki
      *label* `host=${HOSTNAME:-local}` already existed) and a `management.metrics.tags.podName`
      Micrometer tag in both `application.properties`, so a log line, a Loki query, or a
      Prometheus metric can all be traced back to the emitting instance once >1 replica runs.
- [x] **Per-endpoint latency (p95/p99) now visible.** Enabled `management.metrics.distribution.percentiles-histogram.http.server.requests=true` in both services (emits `http_server_requests_seconds_bucket`) and added a "Latency p95/p99 by endpoint" panel to `monitoring/grafana/dashboards/aisandbox-overview.json` (`histogram_quantile` over the buckets). Response time was already partly observable (avg+max via Micrometer, `durationMs` in `AuditInterceptor` logs, k6 p95 at load). Still worth capturing a screenshot from a real load run (ties to the observability item above).
- [x] **Distributed tracing (OpenTelemetry) — implemented, third observability pillar closed.**
      Added `io.micrometer:micrometer-tracing-bridge-otel` + `io.opentelemetry:opentelemetry-exporter-otlp`
      to the shared subproject dependencies (both services get identical tracing, like the
      metrics/logging deps already there); `management.tracing.sampling.probability` (100% —
      fine at this traffic volume) and `management.otlp.tracing.endpoint` wire the OTLP export.
      **Trace context now crosses the Kafka boundary**, the actual hard part of this item:
      `spring.kafka.template.observation-enabled=true` (Auth's producer) and
      `spring.kafka.listener.observation-enabled=true` (Audit's consumer) wrap Spring for Apache
      Kafka's `KafkaTemplate`/listener container in a Micrometer Observation — this is Spring's own
      documented mechanism for propagating trace context through message headers, not custom
      header-plumbing code, so a trace started by e.g. `POST /auth/login` continues through the
      published `AuditEvent` and into Audit's consumer as one trace. Added Tempo to both compose
      files (`monitoring/tempo/tempo.yaml`, OTLP receiver, local disk storage) and a Tempo Grafana
      datasource with `tracesToLogsV2` correlation. That correlation needed a real decision: the
      first draft tried to correlate on a custom `requestId` span tag that nothing actually stamps
      onto spans (would've been a broken-looking feature) — caught it and switched to `traceId`
      substring search instead, backed by adding `traceId=`/`spanId=` to both services' logback
      patterns, which Micrometer Tracing genuinely does populate into MDC automatically once
      tracing is active. Verified: `./gradlew :common:check :Audit:check :Auth:check` green
      (dependency resolution + no coverage regression — this is all config, no new Java code paths
      to test), both compose files + Tempo config YAML-valid. Not run against a live collector
      here (no Docker locally) — the actual trace propagation across the Kafka hop is the one part
      of this whole TODO sweep I'd most want to see confirmed working end-to-end before relying on
      it for an interview demo.
- [x] **OpenShift manifests — PVCs added, `emptyDir` gone.** Added a `pvc.yaml`
      (`ReadWriteOnce`, no `storageClassName` so it binds the cluster default) next to each of
      Loki/Grafana/Prometheus's `deployment.yaml`, sized 5Gi/1Gi/10Gi, and pointed each `data`
      volume at its PVC instead of `emptyDir`. Also added `strategy: Recreate` to all three
      deployments — with a single-replica RWO-backed pod, the default `RollingUpdate` would try to
      schedule the new pod before the old one released the volume mount and hang. `oc apply -f
      openshift/monitoring/<component>/` already applies every file in the directory, so no deploy
      script changes needed. Not cluster-verified here (no live OpenShift access), but YAML-valid
      (parsed with PyYAML). README's `emptyDir` caveat updated to describe the PVC setup instead.
- [x] **Minor security smells — fixed.** (1) `RefreshRequest.refreshToken` is now `@NotBlank`,
      wired with `@Valid` on `/auth/refresh` and `/auth/logout`, and `GlobalExceptionHandler` gained
      a `MethodArgumentNotValidException` handler so a blank/missing token returns 400 instead of
      falling into the catch-all 500 (a real bug this closed, not just a lint fix). (2) CORS is now
      configured on both services — a `CorsConfigurationSource` in `SecurityConfig`, origins from
      `CORS_ALLOWED_ORIGINS` (default `http://localhost:4200`, the Angular dev server), verified with
      a MockMvc preflight test — closing the CORS hard-blocker called out under "Backend seams the
      UI depends on" too. (3) `MdcLoggingFilter.extractSubFromJwt()` in both services now parses the
      payload with Jackson (`ObjectMapper.readTree`) instead of a naive substring search, with the
      same existing tests (valid/malformed/sub-less tokens) passing unchanged.

## Low impact

- [x] **Duplicated `ratelimit/` package — extracted into a real `:common` Gradle module.** Added
      `common` as a third subproject (`settings.gradle`), a plain library (Spring Boot's `bootJar`
      disabled, plain `jar` enabled). Moved `ActiveRequest`/`ActiveRequestRegistry`/`DiscardContext`/
      `RateLimitInterceptor`/`RateLimitProperties`/`RequestDiscardedException` to
      `com.aisandbox.common.ratelimit` (`TransactionalRequestExecutor` stayed in Audit — it's
      JPA-transaction-specific and Auth has no datastore) and `AuditEvent` to
      `com.aisandbox.common.event`. Both services now `implementation project(':common')`. Also
      consolidated the two duplicated ratelimit test suites into one (`common/src/test/...`); the
      one Audit-only cross-cutting test (superseded-request → 429 via `GlobalExceptionHandler`)
      moved into each service's own `GlobalExceptionHandlerTest` — Auth's copy of that test didn't
      exist before despite `GlobalExceptionHandler.handleAll()` having the identical logic, so this
      also closed a real pre-existing coverage gap in Auth, not just deduplicated Audit's. Since
      `common`'s package-private internals (`ActiveRequest`'s constructor, `DiscardContext.set`)
      are no longer same-package with the services' test code, those tests now go through the real
      `RateLimitInterceptor`/`ActiveRequestRegistry` public API instead of reaching in directly —
      arguably a more realistic test than before. `Dockerfile`s (both services) and CI (no hardcoded
      module lists — `./gradlew test`/`jacocoTestCoverageVerification` already run against every
      subproject) updated accordingly. Verified: `./gradlew :common:check :Audit:check :Auth:check`
      all green, including the 90% coverage gate on the new module. Still true and worth
      articulating even now: this pattern won't scale past ~6–8 services without a shared
      platform/BOM module for dependency versions too (not just code) — not built here.
- [x] **Rate limiter's "high concurrency" claim — real numbers now in the README, actually run
      (not just written).** Every other "not verified here" item this session was blocked by no
      Docker/no live infra — this one wasn't: k6 wasn't installed, but `./gradlew` and a JDK were
      right here, so it was fixable. `winget install` hung indefinitely; fell back to downloading
      the k6 v2.1.0 Windows zip directly from GitHub releases and running the extracted binary.
      Then genuinely ran both scripts end-to-end against a locally-started Audit
      (`SPRING_PROFILES_ACTIVE=LOCAL,LOADTEST`, matching CI's own load-test job exactly) and pulled
      the real numbers into the README: search-stats.js — 281 req/s sustained, p95 7.31ms (gate:
      <800ms), 0% failed; rate-limit.js — 715 req/s sustained, 81.5% shed as 429, 0% 5xx (gate:
      <5%). Both scripts' thresholds passed. This is the one item in this entire multi-round TODO
      sweep that went from "documented as unverified" to "actually verified, with the real
      command output to show for it," rather than being a well-reasoned-but-untested change.
