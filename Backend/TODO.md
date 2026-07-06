# TODO — Interview-readiness punch list (Fullstack Engineer)

## Open roadmap (prioritized)

### Product/UI roadmap (portfolio presentation)
- [ ] **GitHub-like dark theme UI restyle.** Give the SPA a cohesive dark theme modelled on
      GitHub's Primer palette (canvas `#0d1117`, borders `#30363d`, accent `#2f81f7`, etc.),
      hand-rolled with global CSS custom properties rather than a UI framework — no Bootstrap
      dependency, full control, GitHub-authentic look, small bundle. One design-token layer in
      `styles.scss` consumed by every component (header/nav, home, login, profile, audit table +
      stat bars, assistant chat, auth-callback). Keep unit tests/lint/prettier/prod build green.
- [ ] **Home page — explain the project (tech stack, design, features, why & how).** Replace the
      thin two-card home with real portfolio content: a tech-stack overview, the key design
      decisions (and *why* each — event-driven audit, RBAC JWTs, stateless services, rate
      limiting, observability, the LLM proxy) and *how* they're implemented, plus a feature tour
      with links into the app and the ADRs. This is the first thing a reviewer sees; it should
      read like the README's pitch, in-app.
- [ ] **Flashcards feature — LLM-generated study deck about the app.** A page that asks the
      Claude proxy (reuse the assistant infrastructure) to generate a deck of Q&A flashcards
      explaining this application's architecture and features — an interview-prep study tool.
      Backend: a new endpoint returning a structured deck (JSON, via a strict output schema),
      grounded on the same app-context doc, behind the same guardrails and RBAC posture as the
      chat assistant (server-side key, no auth headers forwarded, role-scoped context). UI: a
      flip-card deck with next/prev/shuffle. Tests + 90% coverage gate; document the reuse in the
      assistant ADR or a short follow-up note.

### Ops roadmap
- [x] **End-to-end deployment plan — written.** [`docs/deployment.md`](docs/deployment.md) walks
      the whole app from commit to running system: what we deploy, the environment/profile matrix,
      the build-once/promote-by-digest GHCR registry flow (marked planned vs built), config vs
      secret management (incl. the server-side `ANTHROPIC_API_KEY`), stateful backing services and
      their remaining no-SPOF work, the existing `openshift/` apply sequence, the CI→CD promotion
      flow, rollout/rollback with Liquibase expand/contract discipline, DNS/TLS/forwarded-headers,
      production observability, and a first-deploy checklist. Linked from the README's deploy
      section. Grounded on the repo's real artifacts (Dockerfiles, compose, manifests, Actions);
      each item tagged built vs planned so it's honest about what exists.

### CI/CD roadmap (highest interview value for a fullstack role, in order)
- [ ] **Playwright E2E suite against the compose stack.** The biggest remaining gap: nothing
      exercises browser → nginx → Auth → Kafka → Audit → Postgres as one system. CI job:
      `docker compose up`, wait for health, then Playwright logs in via the demo form, filters
      the audit table, adds demo logs, asserts rows/stats. This is the "how do you know the whole
      thing works?" answer.
- [ ] **API contract gate (openapi-diff).** Generate each service's OpenAPI spec in CI and fail
      the PR on breaking changes vs `main`'s spec. Demonstrates treating the UI↔API seam as a
      versioned contract (Pact is the heavier alternative; openapi-diff is 80% of the story for
      20% of the effort — and the repo already URI/header-versions its APIs, so this completes
      that narrative).
- [ ] **CD: versioned images to GHCR on merge to `main`.** Build once, tag SemVer + git SHA,
      push to GitHub Container Registry; compose gets a variant that pulls instead of builds.
      Closes the "CI but no CD" gap and makes release engineering a first-class talking point.
- [ ] **Mutation testing (PIT), report-only first.** The sophisticated answer to "is 90% line
      coverage meaningful?" — prove the tests fail when the code is mutated, not merely execute
      it. Start report-only (like the Trivy image scans did) and gate once the baseline is known.
- [ ] **SBOM (syft) + image signing (cosign).** Cheap to bolt onto the existing Trivy jobs;
      "SBOM/SLSA/provenance" is the current supply-chain vocabulary that distinguishes senior
      candidates, and this repo already has the scanning half of that story.

### Feature: "LLM Chat" — DONE
- [x] **LLM Chat feature — implemented, with role-based data access.** New `assistant/` package
      in Audit: `POST /api/v1/assistant/chat` is a thin server-side proxy calling the Claude API
      via the **official Anthropic Java SDK** (Spring AI considered and rejected — one call site,
      and the hard requirement is knowing exactly which bytes leave for the provider; the
      abstraction earns nothing here). API key is server-env only (`ANTHROPIC_API_KEY`; endpoint
      503s cleanly without it). All five guardrails from this item's spec landed: (1) no auth
      headers/JWTs/cookies can reach the provider *by construction* — the outbound request is
      built solely from screened text + a server-assembled system prompt; (2) `PromptScreener`
      rejects JWT `eyJ…`/Bearer shapes, credential *assignments* (asking about passwords passes),
      emails, and card-like numbers with a canned local reply, and re-screens replayed history
      since the client isn't trusted; (3) data access is allowlisted in one class
      (`AssistantContextBuilder`) — **extended beyond the original aggregate-only plan with RBAC**:
      ROLE_USER gets docs + aggregate stats, ROLE_ADMIN additionally the 20 most recent raw rows
      (role from the verified JWT, gate in the context builder not the prompt); (4) retrieved data
      is tag-wrapped and declared non-instructional (prompt-injection posture); (5) the existing
      newest-wins rate limiter covers the endpoint automatically, and logs carry violation
      category + lengths only. Data-flow ADR:
      [ADR-0009](docs/adr/0009-llm-chat-assistant-data-flow.md). UI: guarded `/assistant` chat
      page (client-held history, blocked replies rendered distinctly, 503 → "not configured"
      hint). Tested at every seam: screener rules, service orchestration, role gating, MockMvc
      security e2e with the provider mocked at the `LlmClient` interface, SDK request assembly
      with a mocked `AnthropicClient`; 90% coverage gate held. Not exercised against the live
      Claude API here (no key in this environment) — first real smoke: export `ANTHROPIC_API_KEY`,
      `docker compose up --build`, ask the Assistant page a question as both roles.

Findings

## Fullstack readiness — Angular microfrontend (ACTIVE FOCUS)
What's needed to credibly present this as a Fullstack Engineer project. Backend is in good
shape (Auth: Google OAuth2 → JWT, JWKS, /auth/me, refresh, logout; Audit: paginated/filtered
search + aggregation; rate limiting; observability; CI). The gaps are the UI and the seams it
needs.

### Frontend — the headline deliverable (`UI/` — Angular 19 SPA, built)
- [x] **Scaffold the Angular workspace in `UI/`.** Angular 19 standalone components, routing,
      ESLint (angular-eslint) + Prettier, Karma/Jasmine unit tests (CI-safe
      `ChromeHeadlessNoSandbox` launcher). `npm`/`ng` build wired.
- [x] **Microfrontend architecture — articulated via ADR, not built.** Chose to document the
      decision rather than build the split (like the API-gateway item): see
      [ADR-0008](docs/adr/0008-no-microfrontend-split.md). Why one standalone SPA beats a
      shell + `auth-mfe` + `audit-mfe` + `shared` federation for a solo-built, two-feature app
      shipped as one artifact — every MFE benefit (independent deploy cadence, team autonomy, tech
      independence) is latent here while every cost (runtime remote loading, shared-dep version
      skew, build/CI complexity) is immediate; Angular lazy routes already give the code-splitting
      win in-app. Names the concrete triggers (independent deploy, separate teams, tech divergence)
      and that `@angular-architects/native-federation` is the tool if they appear. Keeps ADR-0005
      stable (the "MFE gets built" gateway trigger doesn't fire).
- [x] **Auth wiring in the SPA — implemented.** Functional HTTP interceptor adding
      `Authorization: Bearer …` (our APIs only) with single silent refresh + retry on 401,
      functional route guard with `returnUrl`, OAuth fragment handoff (reads `#access_token`, then
      `history.replaceState`), signal-based `AuthService`, and a localStorage token store (tradeoff
      vs httpOnly cookies documented). Login/callback/profile pages.
- [x] **Audit feature UI — implemented.** Server-side paginated + sortable table (page/size/sort),
      filter form (entityType/action/date range), and by-action/by-entityType stats as
      dependency-free CSS bar charts, against `/api/v1/audit-logs/search` + `/stats`. Contract
      matched to the backend DTOs (`PagedResponse` envelope, `AuditLogStats`).
- [x] **Frontend CI + container — done.** CI: a `Frontend CI` workflow (format check, lint, prod
      build, headless unit tests) on `UI/**`, plus the backend required-check path filters broadened
      to `UI/**` so UI-only PRs are mergeable. Container: `UI/Dockerfile` (multi-stage — `npm ci` +
      prod build on node 22 matching Frontend CI, then `nginx:1.28-alpine` serving
      `dist/ai-sandbox-ui/browser`) with `UI/nginx.conf` doing the two jobs the production
      `environment.ts` was already designed around: SPA fallback (`try_files … /index.html`) and
      same-origin reverse proxies `/auth-api/` → `auth:8085` / `/audit-api/` → `audit:8083`, so the
      browser never needs CORS on this path. Hashed bundles get immutable cache headers,
      `index.html` gets `no-cache`. A `ui` service in `Backend/docker-compose.yml` publishes it on
      host `:4200` — the origin `CORS_ALLOWED_ORIGINS`/`FRONTEND_URL` already assumed — so demo
      login + audit dashboard work end-to-end out of one `docker compose up --build`. Follow-up
      fix after a real click-through: the *Google* sign-in path 401'd — with placeholder creds
      that's Google's expected `invalid_client`, but the proxied flow was also genuinely broken:
      the Spring-derived `redirect_uri` came out as `http://localhost/login/oauth2/code/google` —
      port dropped (nginx passed `$host`, which strips it) *and* `/auth-api` prefix missing
      (Spring ignored `X-Forwarded-Prefix` without a forward-headers strategy). Fixed with
      `Host`/`X-Forwarded-Host: $http_host` in nginx (explicitly not `$server_port`, which is the
      container's internal 80, not the published 4200) + `SERVER_FORWARD_HEADERS_STRATEGY=framework`
      on the auth compose service (env-scoped so trusting `X-Forwarded-*` stays limited to the
      deployment where nginx sets them). Verified: the authorize redirect now carries
      `redirect_uri=http://localhost:4200/auth-api/login/oauth2/code/google`. Real Google sign-in
      through the container additionally needs (host-side, documented in the compose header): real
      `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` exported, and that exact redirect URI added to the
      OAuth client in the Google Cloud console. The "(optional) make Frontend CI a required
      branch-protection check" tail was deliberately not done: `Frontend CI` only triggers on
      `UI/**`, so making it required would leave backend-only PRs permanently un-mergeable unless
      its path filters were broadened to everything (the same asymmetry the backend checks solved
      in reverse) — noting it here instead of half-doing it.

- [x] **Audit screen UX round 2 — filter dropdowns, details search, sticky header.** Entity type
      and Action filters are now `<select>` dropdowns instead of free-text — options are the
      accumulated union of every `/stats` bucket key seen so far (the unfiltered initial load
      seeds the full set; narrower stats responses only ever *add*, so applying a filter doesn't
      make the other options vanish — and no hardcoded enum list to drift from the data). New
      **Details "contains" filter**: `details` param on `/search` + `/stats`, implemented in
      `AuditLogSpecifications` as a case-insensitive `LIKE '%…%'` with explicit `\`-escaping of
      `%`/`_`/`\` so a user searching a literal `100%` doesn't trigger wildcard expansion
      (integration-tested: substring match, case-insensitivity, `_`-as-literal, and that
      `aggregate()` honours it identically to `search()`). Layout: app header is now
      `position: sticky` (full-width — `body{margin:0}` also killed the default 8px white ring),
      the audit table's column headers stick just below it (`top: 56px`, solid bg + shadow), and
      the audit page breaks out of `.app-main`'s centered 1100px column to full viewport width
      (`margin-inline: calc(50% - 50vw)` + `overflow-x: clip` on body for the scrollbar-width
      overhang).

- [x] **Demo-log generator — bulk-insert dummy rows on demand.** `POST /api/v1/audit-logs/demo`
      with a validated `{"count": N}` body (1..500 — capped so a typo can't flood the table)
      generates randomized-but-realistic rows server-side (`DemoDataGenerator`, same
      entityType/action vocabulary as the startup seeder so generated rows blend in and exercise
      the dropdowns/stats/details-search). Profile-gated to LOCAL/DEV like the seeder — the
      controller bean doesn't exist in SIT/UAT/PROD, so the route 404s where dummy rows would
      pollute a real audit trail. Runs through `TransactionalRequestExecutor` like every other
      mutation on the API. UI: an "Add demo logs" count input + button on the audit screen that
      posts and reloads table + stats. Fixing this surfaced (and closed) a real pre-existing gap:
      Audit's `GlobalExceptionHandler` had **no `MethodArgumentNotValidException` handler** — Auth
      got one in the "minor security smells" round, Audit never did, so any `@Valid` body failure
      here 500'd instead of 400ing (the exact bug class fixed twice before). Added the handler +
      an `errorBody(String)` overload, and a standalone-MockMvc test asserting out-of-range and
      *missing* counts return 400, not 500 (a missing `count` deserializes to 0 and must fail
      `@Min`, not silently insert zero rows).

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
- [x] **Trivy extended to the Docker images — now a real gate.** The `trivy` job in
      `backend-ci.yml` `docker build`s both `Audit/Dockerfile` and `Auth/Dockerfile` and scans each
      resulting image (not just the boot-jar dependencies), so base-image/OS CVEs (e.g. in
      `eclipse-temurin:17-jre-alpine`) are caught too. Uploaded to the Security tab under distinct
      SARIF categories (`trivy-audit-image`/`trivy-auth-image`, plus `trivy-jars` on the existing
      jar scan, so the three uploads in one job don't overwrite each other). Initially shipped
      report-only — this job is a required branch-protection check, and an existing base-image CVE
      would have locked out every PR on day one. Once the image scans had a confirmed green run on
      `main`, checked the precondition properly before flipping: the 4 open Security-tab alerts in
      the image categories are all Trivy-severity MEDIUM (the SARIF report includes mediums because
      trivy-action doesn't apply the severity filter to SARIF output by default), so a fixable-
      HIGH/CRITICAL gate trips on nothing today. Added dedicated gate steps (`format: table`,
      `severity: HIGH,CRITICAL`, `ignore-unfixed`, `exit-code: '1'`) per image, mirroring the jar
      report+gate step split — deliberately *not* `exit-code` on the SARIF steps themselves, which
      (per that same SARIF quirk) would have gated on the mediums too. The image builds themselves
      are CI-verified green, which also retroactively confirms the earlier Dockerfile `COPY
      Survey/` fix ("do a `docker build` once to confirm" — done, in CI).

## Medium impact

- [x] **ADRs / design-tradeoff docs — added.** New `docs/adr/` with four real decisions and
      alternatives considered: RSA over HMAC for JWT signing, in-memory over Redis for rate
      limiting/refresh tokens (with the multi-replica consequence spelled out), H2 over
      Testcontainers for tests, and Liquibase over Hibernate `ddl-auto` for schema ownership.
      Linked from the root README.
- [x] **Auth refresh-token store is in-memory only — since externalized to Redis.** This item
      allowed either replacing the store or documenting the limitation; originally documented it
      (below), then later actually replaced it under the statelessness item — see
      [ADR-0007](docs/adr/0007-redis-refresh-token-store-for-statelessness.md) and the
      `RefreshTokenStore`/`RedisRefreshTokenStore` in Auth. The in-memory store remains the dev/test
      default; Redis is opt-in via `auth.refresh-token.store=redis`. Original documentation writeup:
      [`docs/adr/0002`](docs/adr/0002-in-memory-rate-limit-and-refresh-store.md)
      covers why `ConcurrentHashMap` was chosen, what breaks past one replica (both the refresh-token
      store and the rate limiter — the *effective* rate limit becomes replicas × configured limit,
      not the configured limit), and that nothing survives a restart. Also fixed a stale claim in
      that same ADR while touching it: it previously said Redis would give refresh-token consumption
      "a real atomic compare-and-delete instead of the current `ConcurrentHashMap.remove()`" — that
      was written before the "Handle concurrency explicitly" item proved `remove()` is already
      atomic per key, so it's now corrected to say Redis is needed for *cross-replica* visibility,
      not to fix a single-process race that doesn't exist. The ephemeral-RSA-keypair half of this
      item is covered by the same ADR (ADR-0001) and the existing code comment in `JwtConfig`.
- [~] **Make services stateless, remove single points of failure, then handle load & scale.**
      Sub-part (1) **stateless — DONE** (see [ADR-0007](docs/adr/0007-redis-refresh-token-store-for-statelessness.md)):
      the refresh-token store is externalized to Redis behind `auth.refresh-token.store`
      (`RefreshTokenStore` interface; `RedisRefreshTokenStore` uses atomic `GETDEL` for single-use
      *across* replicas + key TTL; `InMemoryRefreshTokenStore` stays the dev/test default). The RSA
      signing key was already shareable via `AUTH_RSA_PRIVATE_KEY` (ADR-0001/`JwtConfig`). The
      "rate limiter" needed no change — it's a per-pod thread-interrupt dedup guard, not a counter,
      so it's *correctly* process-local (ADR-0007 explains why, and corrects ADR-0002's "limit ×
      replicas" framing). Auth + Audit deployments and HPAs now run **≥2 replicas** (`openshift/`),
      with a Redis Deployment/Service added and a `redis` service wired into `docker-compose.yml`
      (`docker compose up --scale auth=2` now keeps refresh working). Verified: `./gradlew
      :Auth:check` green (new stores unit-tested incl. the atomic-consume path; 90% coverage gate
      held). Sub-parts still open, all **infra-blocked** (need a live cluster/Docker, none here):
      (2) **no SPOF** — Redis is single-replica (Sentinel/managed HA next), Kafka is a single-broker
      Redpanda dev container (multi-broker next), Postgres is single (read-replica/HA next);
      (3) **load & scale proof** — the k6 scripts pass single-instance, but running them against a
      2-replica stack to prove refresh survives a pod bounce needs the stack actually up.
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
