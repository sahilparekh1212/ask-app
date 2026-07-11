# Code map — a file-by-file guide to the AI-Sandbox repository

This document is a guide to **what each file in the repository does**, grouped by concern.
It exists so the built-in assistant (and any reader) can answer questions like "which files
are tied to the Docker setup?", "where is rate limiting implemented?", or "what handles the
OAuth login redirect?" without reading the whole tree.

How to read it: files are grouped under the concern they belong to. A single file can be
relevant to more than one concern (e.g. `docker-compose.yml` is both Docker and observability),
in which case it is listed under its primary concern and cross-referenced. Paths are relative
to the repository root; the backend lives under `Backend/`, the Angular SPA under `UI/`.

## Repository layout (top level)

- `Backend/` — two Spring Boot services (Auth, Audit) plus the shared `common` module, the
  Docker/compose stack, monitoring configs, OpenShift manifests, docs, and load tests.
- `UI/` — the Angular 19 single-page app (the dashboard, chat, flashcards, login).
- `e2e/` — Playwright end-to-end suite that runs against the real compose stack.
- `.github/` — GitHub Actions workflows (CI, CD, deploy, security scans) and helper scripts.
- `README.md` (root) — the portfolio-facing overview: live URL, architecture diagram, tech stack.
- `Backend/README.md` — the detailed engineering README (run instructions, design notes).

## Docker & container setup

These are the files tied to the Docker / container setup. If you are asked "which files are
tied to the Docker setup?", this is the list:

- `Backend/docker-compose.yml` — the main stack: Postgres (pgvector image), Redpanda + console,
  Prometheus/Loki/Grafana/Tempo, and the Auth, Audit and UI services. `docker compose up --build`
  brings up the whole system on `:4200`.
- `Backend/docker-compose.prod.yml` — production override: Caddy on 80/443 as the only published
  port (every other port withdrawn via `!reset`), `SPRING_PROFILES_ACTIVE=PROD`, real-domain CORS.
- `Backend/docker-compose.ghcr.yml` — override that **pulls** pre-built images from GHCR instead of
  building them (`--no-build`), pinned by `AI_SANDBOX_TAG`; used by the deploy workflow.
- `Backend/docker-compose.scale.yml` — override that runs 2 Auth replicas behind round-robin
  nginx, used to prove statelessness (Redis-backed refresh tokens, deterministic JWKS `kid`).
- `Backend/Auth/Dockerfile` — multi-stage Gradle build → `eclipse-temurin` JRE image for Auth.
- `Backend/Audit/Dockerfile` — multi-stage Gradle build → JRE image for Audit; also COPYs the
  docs corpus into the build so the RAG index is bundled into the jar.
- `Backend/.dockerignore` — keeps build context lean; note it deliberately un-ignores `*.md` so the
  RAG corpus (README + docs/) reaches the Audit image build.
- `UI/Dockerfile` — multi-stage: `npm ci` + Angular prod build on node 22 → `nginx:1.28-alpine`.
- `UI/nginx.conf` — SPA fallback (`try_files … /index.html`) and same-origin reverse proxies
  `/auth-api/` → `auth:8085`, `/audit-api/` → `audit:8083` (so the browser needs no CORS).
- `UI/.dockerignore` — trims the UI build context.
- `Backend/kafka/docker-compose.yml` — standalone Redpanda + console, for a smaller local loop.
- `Backend/monitoring/docker-compose.yml` — standalone Prometheus/Loki/Grafana/Tempo stack.

## Deployment & cloud (GCP, Caddy, OpenShift)

- `.github/workflows/deploy.yml` — deploys to the GCE VM after a successful CD: authenticates to
  GCP keyless via Workload Identity Federation, ships the compose bundle, writes `.env` +
  `secrets/auth_key.pem`, `pull && up -d`, then smoke-checks the public origin. Gated on
  `DEPLOY_ENABLED`.
- `Backend/deploy/Caddyfile` — Caddy config: terminates TLS (auto Let's Encrypt for `$DOMAIN`) in
  front of the UI nginx; the production entry point.
- `Backend/docs/deployment.md` — the end-to-end deployment guide (environments, profiles,
  build-once/promote-by-digest, secrets, rollout/rollback, first-deploy checklist).
- `Backend/openshift/**` — Kubernetes/OpenShift manifests (deployment/service/route/hpa/configmap/
  secret) for Auth, Audit, Redis and the monitoring stack; an alternative to the compose deploy.
- `Backend/openshift/namespace.yaml` — the namespace all the manifests apply into.

## CI/CD & GitHub Actions

- `.github/workflows/backend-ci.yml` — required backend gate: build, unit tests, JaCoCo coverage,
  Spotless, diff-cover, and the Trivy CVE scan (jars + both Docker images).
- `.github/workflows/frontend-ci.yml` — UI gate: format check, lint, prod build, headless unit tests.
- `.github/workflows/lint.yml` — repo-wide hygiene: gitleaks secret scan, detect-private-key,
  conventional-commit lint.
- `.github/workflows/codeql.yml` — CodeQL SAST (java-kotlin) on PR/push + weekly.
- `.github/workflows/api-contract.yml` — PR-only: boots the PR head and base services, generates
  OpenAPI specs from the running code, and fails on breaking (incompatible) contract changes.
- `.github/workflows/cd.yml` — on merge to `main`, matrix-builds all three images, pushes to GHCR
  (SemVer + `sha-` + `latest` tags), then cosign-signs each digest keyless and attaches a syft SBOM.
- `.github/workflows/e2e.yml` — brings up the real compose stack and runs the Playwright suite.
- `.github/workflows/mutation.yml` — PIT mutation testing, report-only (not a merge gate).
- `.github/scripts/generate-openapi-specs.sh` — boots each service on the LOCAL profile and fetches
  springdoc `/v3/api-docs`; used by the API-contract workflow so the spec can't drift from code.
- `.github/dependabot.yml` — weekly gradle + github-actions dependency update PRs.

## Observability & monitoring

- `Backend/monitoring/prometheus/prometheus.yml` / `prometheus-compose.yml` — Prometheus scrape
  config (the compose variant uses Docker DNS `dns_sd_configs` so it sees every replica).
- `Backend/monitoring/loki/loki-config.yaml` — Loki log-aggregation config.
- `Backend/monitoring/tempo/tempo.yaml` — Tempo distributed-tracing (OTLP) config.
- `Backend/monitoring/grafana/provisioning/datasources/datasources.yml` — wires Prometheus/Loki/
  Tempo into Grafana.
- `Backend/monitoring/grafana/provisioning/dashboards/dashboards.yml` — dashboard provisioning.
- `Backend/monitoring/grafana/dashboards/aisandbox-overview.json` — the provisioned overview
  dashboard (per-endpoint p95/p99, error rate, etc.).
- On the app side, tracing/metrics are configured in each service's `application.properties`
  (`management.*`, `spring.kafka.*.observation-enabled`) and log correlation in
  `logging/MdcLoggingFilter.java` + `Audit/src/main/resources/logback-spring.xml`.

## Kafka / event-driven audit pipeline

The audit trail is event-sourced: producers publish `AuditEvent`s to the `audit.events` Kafka
topic and Audit consumes and persists them. Files tied to this pipeline:

- `Backend/common/src/main/java/com/aisandbox/common/event/AuditEvent.java` — the shared Kafka
  message contract (record) both services use, so there is no per-service copy.
- `Backend/Auth/src/main/java/com/aisandbox/auth/event/AuditEventPublisher.java` — Auth publishes
  LOGIN/TOKEN_REFRESH/LOGOUT events (`@Async`, fire-and-forget).
- `Backend/Audit/src/main/java/com/aisandbox/audit/event/AuditEventPublisher.java` — Audit's own
  publisher for the AI features (Assistant/CHAT, Flashcards/GENERATED, Rag/SEARCH, Mcp/TOOL_CALL);
  same `@Async` fire-and-forget posture, publishing back onto `audit.events`.
- `Backend/Audit/src/main/java/com/aisandbox/audit/event/AuditEventConsumer.java` — the
  `@KafkaListener` that persists events as `AuditLog` rows, idempotent by `eventId`.
- `Backend/Audit/src/main/java/com/aisandbox/audit/config/KafkaConsumerConfig.java` — retry +
  dead-letter (`audit.events.DLT`) error handling for the listener.
- `Backend/kafka/docker-compose.yml` — the local Redpanda broker + console.

## Auth service (Java — `Backend/Auth/`)

- `AuthApplication.java` — Spring Boot entry point (`@EnableAsync` for the publisher).
- `controller/AuthController.java` — `/auth/login` (demo login), `/auth/refresh`, `/auth/logout`,
  `/auth/me`; issues JWTs and publishes the login audit events.
- `controller/JwksController.java` — `/.well-known/jwks.json`, the public keys Audit uses to verify JWTs.
- `handler/OAuth2LoginSuccessHandler.java` — after Google OAuth, redirects to the SPA with tokens in
  the URL fragment.
- `service/TokenService.java` — mints/validates access + refresh JWTs, embeds the `roles` claim.
- `service/RefreshTokenStore.java` — Strategy interface for refresh-token storage.
- `service/InMemoryRefreshTokenStore.java` — default single-replica store.
- `service/RedisRefreshTokenStore.java` — Redis-backed store (atomic `GETDEL` single-use across
  replicas) — the piece that makes Auth horizontally scalable.
- `service/RefreshTokenEntry.java` — the stored refresh-token value object.
- `model/LoginRequest.java`, `model/RefreshRequest.java`, `model/TokenResponse.java`,
  `model/Roles.java` — request/response DTOs and the role constants.
- `config/SecurityConfig.java` — Spring Security filter chain, CORS, OAuth2 login wiring.
- `config/JwtConfig.java` — RSA signing key loading (PKCS8 PEM or ephemeral dev key), deterministic
  RFC-7638 `kid`.
- `config/RedisConfig.java` — Redis connection (only used when the refresh store is `redis`).
- `config/OpenApiConfig.java`, `config/WebConfig.java`, `config/AsyncTracingConfig.java` — Swagger,
  MVC/CORS bits, and the task decorator that keeps traces connected across `@Async`.
- `audit/AuditInterceptor.java` — request/response audit logging interceptor.
- `logging/MdcLoggingFilter.java` — puts `requestId`/`userId` into the logging MDC.
- `exception/GlobalExceptionHandler.java` — maps exceptions to clean HTTP statuses.

## Audit service — query API & persistence (`Backend/Audit/`)

- `AuditApplication.java` — Spring Boot entry point (`@EnableAsync` for the AI-feature publisher).
- `controller/AuditLogController.java` — `/api/v1/audit-logs/search` + `/stats` (paginated, filtered,
  aggregated); `delete` is `@PreAuthorize("hasRole('ADMIN')")`.
- `controller/AuditLogV2Controller.java` — the `/api/v2` variant (URI versioning demo).
- `controller/MetaController.java` — `/api/v1/meta/features`, the capability probe the UI uses to
  hide LOCAL/DEV-only affordances (e.g. the "Add demo logs" button) in prod.
- `controller/DemoDataController.java` — `POST /api/v1/audit-logs/demo` bulk-insert (LOCAL/DEV only).
- `service/AuditLogService.java` — search (filtered, paginated, sort-whitelisted) and database-side
  `GROUP BY` aggregation, both from the same JPA Specification.
- `repository/AuditLogRepository.java` — Spring Data JPA repository.
- `repository/AuditLogSpecifications.java` — dynamic filter predicates (entityType/action/date/
  details-contains) shared by search and aggregation so they always agree.
- `model/AuditLog.java` — the immutable audit-row entity (constructors only, one-way `markDeleted`).
- `model/AuditableEntity.java` — base class with `createdAt`/`updatedAt`/`createdByRequestId`.
- `service/DemoDataGenerator.java` — realistic random rows for the demo endpoint/seeder.
- `config/DemoDataSeeder.java` — seeds ~15 rows on startup under LOCAL/DEV.
- `ratelimit/TransactionalRequestExecutor.java` — runs mutations through the shared rate-limit/
  transaction machinery.
- `dto/*` — `PagedResponse`, `AuditLogStats`, `AuditLogCount`, `AuditLogFilter`, `DemoDataRequest`,
  `DemoDataResponse`, `FeaturesResponse` (the API envelopes and filter/aggregate shapes).
- `config/SecurityConfig.java` — JWT resource-server config, role mapping, method security.
- `config/LoadTestSecurityConfig.java` — relaxed security for the `LOADTEST` profile.
- `config/JpaAuditingConfig.java`, `config/WebConfig.java`, `config/OpenApiConfig.java` — JPA
  auditing, MVC/CORS, Swagger.
- `exception/GlobalExceptionHandler.java` — validation/access/other exceptions → correct statuses.
- `logging/MdcLoggingFilter.java` — MDC correlation for Audit.

## Audit service — AI features: Assistant (chat) & Flashcards

- `assistant/AssistantController.java` — `POST /api/v1/assistant/chat`, derives role from the JWT.
- `assistant/AssistantService.java` — orchestrates one chat turn: screen → retrieve (RAG) → call the
  provider; emits the `Assistant/CHAT` audit event.
- `assistant/AssistantContextBuilder.java` — **the data-access allowlist and system-prompt builder**:
  assembles the role-scoped prompt (docs + aggregate stats for USER; also recent raw rows for ADMIN)
  and appends retrieved doc chunks. This is where the assistant's behavior rules live.
- `assistant/PromptScreener.java` — blocks credentials/tokens/emails/card-like numbers before any
  provider call, with category-only logging.
- `assistant/LlmClient.java` — the provider seam (interface) that keeps guardrail logic testable.
- `assistant/AnthropicLlmClient.java` — the real implementation using the official Anthropic Java SDK.
- `assistant/AssistantProperties.java` — server-side API key, model, max-tokens config.
- `assistant/AssistantUnavailableException.java` — maps missing-key / provider failures to 503.
- `assistant/FlashcardController.java` — `POST /api/v1/assistant/flashcards`.
- `assistant/FlashcardService.java` — generates a Q&A deck via the same proxy + grounding; emits the
  `Flashcards/GENERATED` audit event.
- `assistant/dto/*` — `ChatRequest`, `ChatResponse`, `ChatTurn`, `Flashcard`, `FlashcardDeck`,
  `FlashcardRequest`.
- `Backend/Audit/src/main/resources/assistant/app-context.md` — the static app-overview document that
  is always included in the assistant's prompt (the "what is this app" grounding).

## Audit service — RAG & MCP (retrieval over the repo's own docs)

- `rag/RagService.java` — the retrieval facade: embeds a query and returns the best-matching chunks;
  used by both chat grounding and the MCP search tool.
- `rag/RagIndexer.java` — builds/refreshes the vector index at startup, incremental by content hash.
- `rag/CorpusLoader.java` — loads the corpus from the classpath (`rag-corpus/**` = the README + docs/,
  plus `app-context.md`). **This is why adding a file under `Backend/docs/` adds it to the chat's
  knowledge**: the build copies `docs/**/*.md` into `rag-corpus/`.
- `rag/MarkdownChunker.java` — splits documents into chunks by markdown heading.
- `rag/EmbeddingClient.java` — the embeddings seam (interface).
- `rag/VoyageEmbeddingClient.java` — Voyage AI embeddings implementation (`voyage-3.5-lite`).
- `rag/VectorStore.java` — the vector-store Strategy interface.
- `rag/InMemoryVectorStore.java` — exact brute-force store (LOCAL/tests; H2 has no pgvector).
- `rag/PgVectorVectorStore.java` — pgvector implementation (cosine `<=>` over Postgres) for the stack.
- `rag/RagProperties.java` — RAG config (enabled, API key, model, dimension, top-k).
- `rag/DocChunk.java`, `rag/ScoredChunk.java`, `rag/SourceSummary.java` — the chunk value objects.
- `rag/mcp/McpController.java` — the hand-rolled Model Context Protocol server (`POST /mcp`): exposes
  `search_knowledge` and `list_sources`; emits `Rag/SEARCH` and `Mcp/TOOL_CALL` audit events.
- `Backend/docs/code-map.md` — **this file**; part of the corpus, so the assistant can answer
  file-location and "which files do X" questions.

## Rate limiting (shared — `Backend/common/`)

"Newest wins per user+endpoint": a new request for the same key interrupts and rolls back the
superseded one.

- `common/ratelimit/RateLimitInterceptor.java` — the HandlerInterceptor entry point.
- `common/ratelimit/ActiveRequestRegistry.java` — tracks the in-flight request per user+endpoint key.
- `common/ratelimit/ActiveRequest.java` — one in-flight request (thread handle + discard state).
- `common/ratelimit/DiscardContext.java` — checkpoint/rollback of a superseded request.
- `common/ratelimit/RequestDiscardedException.java` — thrown when a request is superseded → 429.
- `common/ratelimit/RateLimitProperties.java` — enable flag + retry-after config.

## Database & migrations

- `Backend/Audit/src/main/resources/db/changelog/db.changelog-master.yaml` — the Liquibase changelog
  that owns the schema: creates `audit_logs` + its indexes and the pgvector `rag_chunk` table.
  Hibernate runs `ddl-auto=none` so Liquibase is the single source of truth.
- `Backend/Audit/src/main/java/com/aisandbox/audit/config/JpaAuditingConfig.java` — enables JPA
  auditing (timestamps).

## Configuration & profiles

- `Backend/Auth/src/main/resources/application.properties` — Auth common config; per-profile files
  `application-{LOCAL,DEV,SIT,UAT,PROD}.properties`.
- `Backend/Audit/src/main/resources/application.properties` — Audit common config (Kafka, RAG,
  assistant, rate limiter, tracing); per-profile files `application-{LOCAL,DEV,SIT,UAT,PROD}.properties`.
- `Backend/Audit/src/main/resources/logback-spring.xml` — structured logging + the loki4j appender
  (active in DEV/SIT/UAT/PROD).
- `UI/src/environments/environment.ts` / `environment.development.ts` — the SPA's API base URLs
  (same-origin `/auth-api` `/audit-api` in prod, direct ports in dev).
- `Backend/build.gradle`, `Backend/settings.gradle`, and each module's `build.gradle` — the Gradle
  build (Spotless, JaCoCo, PIT, dependencies; `Audit/build.gradle` also bundles the RAG corpus).

## Frontend (Angular SPA — `UI/`)

For element-level detail — which component owns a given input/button/form and which backend
endpoint each page calls — see [`ui-guide.md`](ui-guide.md). This section is the file index.

- `src/app/app.component.ts/.html`, `app.config.ts`, `app.routes.ts` — the root component, DI/config,
  and route table (lazy feature routes, guarded).
- `src/app/core/auth/auth.service.ts` — signal-based auth state; login/refresh/logout.
- `src/app/core/auth/auth.interceptor.ts` — attaches the Bearer token and does one silent
  refresh-and-retry on 401.
- `src/app/core/auth/auth.guard.ts` — route guard with `returnUrl`.
- `src/app/core/auth/token-storage.service.ts` — localStorage token store.
- `src/app/core/auth/auth.models.ts` — auth types.
- `src/app/features/audit/*` — the dashboard: `audit.component` (table + filters + stat bar charts),
  `audit.service` (calls `/search` + `/stats`), `audit.models`.
- `src/app/features/assistant/*` — the chat page: component, `assistant.service`, models.
- `src/app/features/flashcards/*` — the flip-card study page: component, service, models.
- `src/app/features/login/*` — the demo-login + Google sign-in page.
- `src/app/features/auth-callback/*` — reads the OAuth token fragment and stores it.
- `src/app/features/profile/*` — the signed-in profile page.
- `src/app/features/home/*` — the About/portfolio landing page.
- `UI/nginx.conf`, `UI/Dockerfile` — see the Docker section.
- `UI/eslint.config.js`, `UI/karma.conf.js` — lint and headless-test config.

## End-to-end & load testing

- `e2e/tests/audit-flow.spec.ts` — Playwright flows against the real stack: demo login lands on
  `/profile`, the login's AuditEvent is polled until it appears as a row, demo-log generation grows
  the total, and a filter asserts stats total == pager total.
- `e2e/playwright.config.ts`, `e2e/package.json` — Playwright config and its own lockfile.
- `Backend/load-test/rate-limit.js` — k6 script exercising the newest-wins rate limiter.
- `Backend/load-test/search-stats.js` — k6 script for throughput/p95 on search + stats.

## Documentation & ADRs

- `README.md` (root) — portfolio overview: live URL, Mermaid architecture diagram, tech stack, run
  instructions.
- `Backend/README.md` — detailed engineering README.
- `Backend/TODO.md` — the prioritized punch-list / roadmap (what is built vs planned).
- `Backend/docs/deployment.md` — the deployment guide (see Deployment section).
- `Backend/docs/adr/0001…0010-*.md` — Architecture Decision Records (RSA JWT signing, in-memory rate
  limit/refresh store, H2 over Testcontainers, Liquibase schema ownership, no API gateway,
  fire-and-forget audit events, Redis refresh store for statelessness, no microfrontend split, LLM
  chat data flow, RAG/MCP server) — each with the alternatives considered and revisit triggers.
- `Backend/docs/adr/README.md` — index of the ADRs.
- `Backend/Audit/src/main/resources/assistant/app-context.md` — the assistant's always-on app overview.
- `Backend/docs/code-map.md` — this file.
