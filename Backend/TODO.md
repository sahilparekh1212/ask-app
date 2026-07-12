# TODO — Interview-readiness punch list (Fullstack Engineer)

## Open roadmap (prioritized)

### Deployment: live on Google Cloud, deployed from GitHub (NEXT UP)
Chosen shape: one GCE VM running the existing compose stack via `docker-compose.ghcr.yml`
(the CD workflow already pushes signed images to GHCR on every merge), deployed by a GitHub
Actions workflow authenticated with Workload Identity Federation. GKE Autopilot + Cloud SQL
(pgvector-capable) + Memorystore was considered and deferred: ~3x the cost, and Kafka still
needs self-hosting either way — worth an ADR when/if this ships, same "considered
alternative" treatment as ADR-0005/0008.

- [x] **GCP foundation — done.** Project `divine-camera-500401-g9`, Compute Engine + Secret
      Manager APIs enabled, static IP `34.139.83.81` (`ai-sandbox-ip`, us-east1), firewall
      `allow-web` (tcp:80,443, tag `web`), VM `ai-sandbox-vm` (us-east1-b, e2-standard-2,
      Debian 12, 50GB) with Docker installed and usable without sudo.
- [x] **Domain + HTTPS — done.** `sahilparekh1212.com` bought at Porkbun; A record
      `ai-sandbox` → `34.139.83.81` (Porkbun's default wildcard-parking CNAME had to be
      deleted — it shadowed the subdomain), verified resolving via 8.8.8.8. TLS = the new
      `caddy` service in `docker-compose.prod.yml` + `deploy/Caddyfile` (auto Let's Encrypt
      for `$DOMAIN` in front of the ui nginx). Prod origin:
      `https://ai-sandbox.sahilparekh1212.com`.
- [ ] **Google OAuth prod client — URIs done, consent screen pending.** Both redirect URIs
      (prod + localhost) are on "Web client 1" and real client id/secret are deployed. Still
      open: the consent screen is in Testing mode, so Google sign-in works only for listed
      test users — publish the app (or add recruiters as test users) before sharing; the
      demo login works for everyone regardless. Also untested: a live Google sign-in
      click-through on the prod domain.
- [x] **Secrets + arm the deploy — done.** All 8 repo secrets + `DEPLOY_DOMAIN`/
      `DEPLOY_ENABLED` variables set; WIF pool/provider/SA created and verified (first
      attempt failed with `iam.serviceAccounts.getAccessToken` denied — the
      `workloadIdentityUser` binding had been created with an empty `$PROJECT_NUM` from a
      new Cloud Shell session; re-running the binding with the resolved project number
      fixed it — a good "env vars don't survive Cloud Shell sessions" war story); GHCR
      packages public (verified anonymously pullable). **First deploy succeeded**:
      https://ai-sandbox.sahilparekh1212.com live with valid Let's Encrypt TLS, demo login
      issuing JWTs, audit stats showing LOGIN events flowing through Kafka in prod, MCP
      endpoint answering.
- [x] **Fund the provider accounts — done, both features verified live.** Voyage payment
      method added (the 10K-TPM no-payment-method cap had 429'd the indexer's first batch)
      → after an audit restart the index built: **132 chunks from all 14 docs** in prod
      pgvector, semantic search verified through the public MCP endpoint ("why is there no
      API gateway?" → ADR-0005; a deployment question → the README's OpenShift section).
      Anthropic credits bought (the 503 was the credit-balance 400) → assistant verified
      live with a real grounded reply through the public origin. War-story footnote: right
      after funding, `list_sources` briefly showed 12/14 docs — the verification query had
      raced the indexer mid-upsert; the `RAG index ready: 132 chunks from 14 docs` log line
      (and a later re-query) settled that nothing was missing. Also on launch night, three
      deploy-pipeline transients were root-caused and fixed (PRs #72/#74): a WIF binding
      created with an empty `$PROJECT_NUM` (Cloud Shell env vars don't survive sessions),
      the smoke check racing Let's Encrypt's first issuance, and gcloud's short-TTL implicit
      OS Login key expiring mid-run — the merge→CD→Deploy chain then ran fully green.
- [x] **Make the app work on mobile — responsive pass done.** Added a single `max-width: 640px`
      breakpoint per component (no framework): the header tightens its padding/gaps and the tab
      row scrolls horizontally instead of wrapping or shoving the avatar off-screen; the audit
      **table becomes a list of cards** on phones (`table/tr/td → display:block`, `thead` hidden,
      each cell shows its column name via a `data-label` attribute + `::before`), so a fixed 230px
      time column no longer forces horizontal scroll; the filter + demo-log controls stack
      full-width for tapping; the flashcards controls wrap full-width. Chat's composer and the
      home page's `auto-fit`/`minmax` grids were already fluid. Verified at a real 375px viewport
      (Angular dev server on 4200 against the running backend) across login, dashboard (cards +
      stacked filters), flashcards, chat, and the nav — screenshots confirmed no horizontal
      scroll and one-row nav everywhere. Frontend-CI gates all green (prod build, ESLint,
      Prettier, 49 unit tests). Still open as honest follow-ups (not blockers): a Playwright
      mobile-emulation e2e and a Lighthouse mobile score for a measured number.
- [ ] **Update README for public use.** Lead with the live URL
      (https://ai-sandbox.sahilparekh1212.com), the demo login (`demo`/`demo`), and the
      public MCP endpoint (`claude mcp add --transport http ai-sandbox
      https://ai-sandbox.sahilparekh1212.com/audit-api/mcp`); reframe the local
      `docker compose up` instructions as the "run it yourself" alternative; consider a
      screenshot/GIF of the live dashboard now that one exists.
- [ ] **Load test the deployed stack + record results.** Decide scope deliberately: the k6
      suite currently runs against a LOADTEST-profile CI stack; prod is one shared 8GB VM
      with the rate limiter ON and real provider keys (assistant/RAG endpoints cost money
      per call — exclude them). A short off-peak k6 smoke against
      https://ai-sandbox.sahilparekh1212.com (search/stats endpoints, modest VUs) answers
      "how does the $50 VM hold up" honestly; publish p95/throughput in the README next to
      the CI numbers.
- [ ] **Set up the observability stack for prod.** The four containers
      (Grafana/Prometheus/Loki/Tempo) ship in the compose stack and start on the VM, but
      "running" ≠ "set up" — verify each actually works against the *deployed* services, not
      just locally: (a) Prometheus is scraping both Auth and Audit on the VM (the compose
      variant uses Docker DNS `dns_sd_configs`, not host ports — confirm targets are UP);
      (b) Loki is receiving logs from the prod containers (the loki4j appender is only active
      in DEV/SIT/UAT/PROD — confirm `LOKI_URL` resolves on the VM and log lines carry the
      right `app` label, the bug that bit locally in PR #64); (c) Tempo is getting traces via
      the OTLP endpoint and a login→Kafka→persist flow shows as one trace; (d) Grafana's
      provisioned dashboards + datasources load and are populated. Then decide exposure:
      currently unpublished (SSH-tunnel only, see the runbook item below) — for a live
      portfolio demo, put read-only Grafana behind Caddy on a subdomain
      (`grafana.ai-sandbox.sahilparekh1212.com`) with basic-auth or Grafana anonymous
      viewer, and change the default `admin`/`admin`. Provider note: none needed — it's all
      self-hosted OSS.
- [ ] **Prod monitoring runbook.** Grafana/Prometheus/Loki/Tempo run on the VM but are
      deliberately unpublished; access via SSH tunnel:
      `gcloud compute ssh ai-sandbox-vm --zone=us-east1-b -- -L 3000:localhost:3000 -N`
      then http://localhost:3000. First login: change Grafana's default `admin`/`admin`.
      Document this in docs/deployment.md; revisit trigger for proper exposure: wanting
      recruiters to see dashboards live (would need auth in front — Caddy basic-auth
      subdomain or Grafana anonymous read-only).
- [x] **GitHub → GCP deploy workflow — built (dormant until armed).**
      `.github/workflows/deploy.yml`: runs after a successful CD on main (plus
      `workflow_dispatch` for the first deploy), authenticates keyless via WIF, ships the
      compose bundle + monitoring configs to `/opt/ai-sandbox`, writes `.env` (single-line
      secrets) and `secrets/auth_key.pem` (multi-line PEM can't live in `.env`; it's
      exported into the shell that runs compose — shell env beats `.env` in precedence),
      then `pull && up -d --no-build`, then smoke-checks `/`, `/audit-api/actuator/health`
      and an MCP `ping` through the public origin. Gated on `DEPLOY_ENABLED` so merging is
      safe pre-setup. Stretch (still open): `cosign verify` digests pre-start.
- [x] **`docker-compose.prod.yml` override — built.** Caddy on 80/443 is the only published
      port (every other service's ports withdrawn via `!reset`, verified with
      `docker compose config` — Grafana/Prometheus now SSH-tunnel-only);
      `SPRING_PROFILES_ACTIVE=PROD` (WARN logging, no seeder/demo-log endpoint — the
      recruiter demo login stays, it's property-gated not profile-gated); real-domain
      CORS/FRONTEND_URL; `DB_PASSWORD` for Postgres + Audit; restart policies. Redpanda
      already runs `--mode=dev-container --smp=1` — no further trimming needed.
- [ ] **Backups.** Nightly `pg_dump` to a GCS bucket (one cron line — the honest "backups
      exist" answer). Note the volume also now carries the pgvector `rag_chunk` index; a
      lost index self-heals on restart via the content-hash indexer, so audit rows are the
      only data that actually needs the backup.
- [x] **Make RAG indexing non-blocking on startup (audit availability on deploy) — implemented.**
      `RagIndexer.run()` now just spawns a named daemon thread (`rag-indexer`) and returns, so the
      `ApplicationRunner` no longer holds the Audit service's readiness hostage to hundreds of
      Voyage embedding calls (~1 min of `/audit-api` unavailability on every deploy/restart with
      the repo-as-corpus — it's what made the first post-deploy smoke check race the boot). The
      try/catch safety net moved into a package-private `indexSafely()` between `run()` and
      `index()`, so "indexing failure never fails (or now delays) the app" stays directly testable
      without thread machinery. Retrieval degrades gracefully while the index warms (chat falls
      back to the un-retrieved prompt; MCP `search_knowledge` reports "not configured/empty"), so
      nothing user-facing needed to change. Test split exactly as this item predicted: the pipeline
      tests call `index()` directly, the failure test calls `indexSafely()`, and a new
      `runIndexesOnABackgroundThreadWithoutBlockingStartup` asserts `run()` hands off to the
      background thread (Mockito `timeout()` verify — no sleeps). Full Audit suite + 90% coverage
      gate + Spotless green.
- [x] **Give the deploy smoke check patience for the slower audit boot — landed with the item
      above.** The deploy workflow's post-deploy smoke check assumed "homepage up ⇒ audit up" and
      gave the audit health + MCP probes only a 50s allowance after the homepage probe; with the
      slow audit startup this item's sibling fixed, a deploy went red on a premature check even
      though prod was healthy a minute later (the PR-#78 deploy — prod was verified live, only the
      badge was red; a re-run once audit was up went green). Fix: health + MCP probes get the same
      40×10s ~7-min window as the homepage probe. The "how to land a `.github/`-only change past
      the path-filtered required checks" question answered itself: it shipped in the same PR as
      the non-blocking-indexing Java change, which trips the required checks normally. Belt and
      suspenders with the async fix — the patience covers any future slow-boot cause, not just
      this one.
- [ ] **Post-deploy verification + surface decisions.** Live click-through: Google OAuth,
      demo login, audit dashboard, `/mcp` handshake (`claude mcp add --transport http
      ai-sandbox https://<domain>/mcp`), Grafana. Decide deliberately: keep `demo`/`demo`
      in prod (recruiter-friendly) vs drop it; the demo-log generator and seeder already
      don't exist outside LOCAL/DEV.

### Dashboard — make it meaningful and relevant (NEXT UP)
The prod dashboard is sparse because the audit trail only records Auth's `LOGIN` events; for
a project that's fundamentally an AI sandbox (chat, flashcards, RAG, MCP) it should show what
the app actually does. Four items; items 1 (AI features emit domain events) and 2 (the time
dimension) have landed, so item 3 (KPI cards — cheap now the aggregations exist) is next up.
Context: the
"Add demo logs" button is now hidden in prod (a LOCAL/DEV-only affordance — see the
`/api/v1/meta/features` capability probe), so prod won't be padded with dummy rows; real usage
should populate it instead.
- [x] **1. Emit audit events from the AI features (headline; unblocks 2–4) — implemented.** The
      AI features all live *inside* the Audit service, so rather than persist rows inline they
      now publish through the same `audit.events` Kafka topic Auth uses and are consumed back by
      Audit's own `AuditEventConsumer` — one uniform event-sourcing path for every producer
      (another service or this one), exercising the Kafka pipeline for real feature traffic. New
      `com.aisandbox.audit.event.AuditEventPublisher` mirrors Auth's exactly: reuses the shared
      `com.aisandbox.common.event.AuditEvent` contract, `@Async` + fire-and-forget (with
      `@EnableAsync` on `AuditApplication` and `max.block.ms=5000` on the producer, both mirrored
      from Auth) so a slow/absent broker never blocks or fails a feature request — the ADR-0006
      at-most-once posture applies unchanged. Four shapes, each at its own service boundary with
      **non-PII** `key=value` detail only (never message/query/card text): `Assistant/CHAT`
      (`AssistantService` — `blocked`, `model`, `latencyMs`, `retrievedChunks`; the blocked turn
      emits too, carrying the screener *category* name, not the matched value), `Flashcards/
      GENERATED` (`FlashcardService` — `model`, `requested`, `produced`, `latencyMs`), `Rag/SEARCH`
      and `Mcp/TOOL_CALL` (`McpController` — `search_knowledge` maps to `Rag/SEARCH` since it *is*
      a semantic search; other tools like `list_sources` map to `Mcp/TOOL_CALL`; detail carries
      `tool`, `latencyMs`, `error`). Deliberate no-double-count decision: chat's internal RAG
      grounding is captured as the `retrievedChunks` field on the CHAT event rather than a second
      `Rag/SEARCH` row, so every user-facing action is exactly one event — matching the TODO's
      own "retrieved-chunk count" detail hint. Events emit only on genuinely-completed actions
      (not on unconfigured 503s, provider failures, or protocol-level tool errors). Tested at each
      seam: new `AuditEventPublisherTest` (keying, fire-and-forget failure swallow) plus emit +
      no-emit + no-PII-leak assertions added to `AssistantServiceTest`/`FlashcardServiceTest`/
      `McpControllerTest`; full Audit suite + 90% coverage gate + Spotless all green locally. Not
      yet seen populating a live dashboard (needs the deploy) — but the pipeline it rides is the
      same one the E2E suite already asserts end-to-end for LOGIN.
- [x] **2. Add a time dimension — implemented.** Backend: `GET /api/v1/audit-logs/stats/timeline`
      (same filter params as `/search` + an `interval=hour|day` enum) runs a database-side
      `GROUP BY date_trunc(...)` Criteria query reusing the identical `AuditLogSpecifications`
      predicate as search/stats, so the trend counts exactly the rows the table shows; the unit
      string is whitelisted by the `TimelineInterval` enum, never request text. Three honest
      findings along the way: (1) `date_trunc` works on H2 as well as Postgres, but truncates in
      the *DB session's* time zone — UTC in the prod containers, local on a dev machine — so the
      integration tests compute expected buckets via `ZoneId.systemDefault()` (documented on the
      service) and the UI anchors its axis on the returned buckets instead of assuming UTC tops;
      (2) a bad query param (`interval=week`, garbage `from` date) fell into the catch-all → 500,
      the exact bug class fixed twice before — added the `MethodArgumentTypeMismatchException`
      handler → 400 + tests; (3) caught only in the live click-through, not by the unit tests
      that call the controller method directly: Spring's default `@RequestParam` enum binding is
      case-sensitive `Enum.valueOf`, so the documented `interval=hour` (and even the
      `defaultValue`) 400'd on the wire — fixed with a case-insensitive `TimelineInterval.from()`
      registered as the String→enum converter in `WebConfig`. UI: an "Events over time" column
      chart in the stats panel
      (dependency-free, same token palette as the bar charts) with a Last 24h (hourly) / Last 7d
      (daily) toggle; empty buckets are zero-filled client-side so quiet periods render as gaps;
      per-column tooltips + first/last axis labels; four new i18n keys translated in all nine
      dictionaries. The chart honours the live filter form (window intersected with the form's
      date range). Tested both sides: 3 timeline integration tests + controller/handler tests
      (Audit suite, 90% gate, Spotless green); 3 new UI specs incl. a zero-fill axis assertion
      (52 unit tests green, lint/format/prod build clean — `anyComponentStyle` warning budget
      bumped 4→6kB since the audit dashboard's legitimate styles now exceed it; error stays 8kB).
- [ ] **3. KPI summary cards on top.** A headline row: total events (24h), busiest feature,
      blocked/error rate, unique event types — instant "what's happening", the standard
      dashboard pattern. Cheap once the aggregation endpoints exist.
- [ ] **4. Frame it against the observability stack.** Position this dashboard as the
      *domain/business* view ("what users and agents did") complementing Grafana's *system*
      view ("how the servers are performing") — a short About/README note plus optionally a
      link out to Grafana. The contrast is itself an interview talking point.

### Feature: RAG MCP server with a vector DB
- [x] **Assistant reads the actual backend source (repo-as-corpus) — implemented.** Extended the
      RAG corpus beyond docs to include the **backend source itself** (Auth + Audit + common Java,
      resources, and the Gradle build files), so the prod chat can read and quote the real deployed
      code, not just its documentation. `CodeChunker` splits source by size on line boundaries
      (line-range labels); `RagIndexer` dispatches markdown-vs-code per file; `CorpusLoader` loads
      both; `Audit/build.gradle` bundles the source into `rag-corpus/` and `Audit/Dockerfile` gained
      `COPY Auth/src` so Auth's source exists in the build stage. Chosen shape (of the two the user
      weighed): **build-time bundling**, not a runtime `git clone` on the VM — because the image is
      built from `main` (merge → CD → deploy), the indexed source is *by construction* the deployed
      code, with no drift, git, network, or auth (public repo → read-only is inherent), keeping
      ADR-0010's self-contained-image property. Scope limit: only the Audit image's Docker build
      context (`Backend/`) can be bundled, so backend source is in but `UI/` and the compose/CI YAML
      above `Backend/` are not (code-map + ui-guide cover those); recorded in the ADR-0010 addendum.
      Verified live: index grew to 336 chunks / 130 docs (174 source chunks newly embedded), and the
      chat accurately quoted `AuditEventPublisher`'s topic/keying/`@Async` posture straight from the
      source. Tested (`CodeChunkerTest`; `CorpusLoaderTest` asserts a `.java` + a Gradle file are
      bundled; `RagIndexerTest` covers the code path); 90% gate + Spotless green.
      **Extended to the UI + observability stack:** the corpus now also bundles the **Angular UI
      source** (`UI/src`) and the **observability/deployment configs** (`monitoring/`, the compose
      files, `openshift/`). Observability lives under `Backend/` so it was a straight COPY + include;
      `UI/` is outside the Audit image's build context, so it's supplied as a **named build context**
      (`--build-context ui=../UI`, copied to `/UI` so the Gradle `../UI` path resolves identically
      host-vs-container — no host-only artifact). Every Audit-image build site passes it:
      `docker-compose.yml` (`additional_contexts`), `cd.yml` (`build-contexts`), and the Trivy build
      in `backend-ci.yml` (`--build-context`). Verified live in the rebuilt image (459 chunks / 219
      docs; 47 UI files, 8 monitoring, 29 openshift indexed): the chat read `auth.interceptor.ts`'s
      401 refresh-retry and compared all three Prometheus configs (static vs `dns_sd_configs` vs
      OpenShift) from the actual files. Recorded in ADR-0010 addendum 2. **Note:** the three CI/build
      changes are verified locally via `docker compose build` and the mirrored
      `docker build --build-context ui=../UI` command; the `cd.yml` `build-contexts` input can only
      be exercised on a real push — watch that CD run when this ships.
- [x] **Assistant explains the codebase + turns stack traces into IDE prompts — implemented.**
      Two changes on top of the RAG assistant so it can "explain everything" about the repo, not
      just the app's behaviour. (1) **Dataset:** a new file-by-file [`docs/code-map.md`](docs/code-map.md)
      summarises every significant source/config/infra file, grouped by concern (Docker, CI/CD,
      Kafka pipeline, Auth, Audit, RAG/MCP, Assistant, UI, observability, DB, docs). The concern
      headings double as retrieval anchors — heading-based chunking means "which files are tied to
      the Docker setup?" pulls the whole Docker group as one chunk. It rides the existing corpus
      path (`docs/**/*.md` → `rag-corpus/` → `CorpusLoader`), so it indexed automatically on rebuild
      (150 chunks / 15 docs, 18 newly embedded). (2) **Prompt:** `AssistantContextBuilder`'s chat
      system prompt now (a) permits codebase/file questions and tells the model to list relevant
      file paths with a one-line role each, drawing only on the code map (no invented paths), and
      (b) handles a pasted error/stack trace by naming the likely files and then emitting a single
      ready-to-paste prompt for an IDE assistant (Claude Code / GitHub Copilot) in a fenced code
      block — goal, quoted error line, files to inspect, root-cause ask. Considered but dropped:
      bundling the repo-ROOT README into the corpus — the Audit image's Docker build context is
      `Backend/`, so anything above it is unreachable at image-build time; copying it would only
      populate the host/CI build and give a corpus that differs from the shipped container (the
      backend README + code map already cover retrieval). Tested (`CorpusLoaderTest` asserts the
      code map is bundled; `AssistantContextBuilderTest` asserts the new prompt rules) and verified
      live against the running stack: the docker-files question returned the exact file list, and a
      Kafka-consumer NPE stack trace produced a correct IDE prompt naming `AuditEventConsumer`/
      `AuditLog`/`AuditEvent` and even flagging the DLT/fire-and-forget nuance from ADR-0006.
      Follow-up: added a component-level [`docs/ui-guide.md`](docs/ui-guide.md) (each Angular page →
      its component/template, interactive elements, service, and the backend endpoint it hits)
      after a live question ("which component has the chat input line and what endpoint?") exposed
      that the code map indexed the UI *files* but not their *elements/endpoints* — the assistant
      now answers it crisply (Assistant page → `assistant.component.html` composer input →
      `assistant.service.ts` → `POST /api/v1/assistant/chat`).
- [x] **RAG MCP server backed by a vector database — implemented.** New `rag/` package in
      Audit + `POST /mcp`, a Model Context Protocol server (Streamable HTTP, stateless
      subset) exposing `search_knowledge` + `list_sources` over this repo's own knowledge
      (backend README, ADRs, `docs/`, `app-context.md`) — demo:
      `claude mcp add --transport http ai-sandbox http://localhost:8083/mcp`. All five
      scoped decisions made and recorded in [ADR-0010](docs/adr/0010-rag-mcp-server.md):
      (1) **pgvector** on the existing Postgres (compose image → `pgvector/pgvector:pg16`,
      `dbms: postgresql`-gated Liquibase changeset for `rag_chunk` with `vector(1024)`),
      behind a `VectorStore` interface with an exact in-memory fallback for LOCAL/tests
      where H2 can't host the extension. (2) **Voyage AI embeddings** (`voyage-3.5-lite`,
      Anthropic has no embeddings endpoint) with the assistant's server-side-key posture:
      no `VOYAGE_API_KEY` → indexing skipped, tools say "not configured", nothing fails.
      (3) **Heading-based chunking + content-hash incremental indexing at startup** (seeder
      precedent); corpus bundled into the jar at build time by `processResources`, so
      re-indexing on doc change = rebuild + restart, and unchanged chunks cost zero
      embedding calls. (4) **Assistant seam composes with the allowlist**: retrieved chunks
      land in a `<retrieved_docs>` tag via `AssistantContextBuilder`; corpus holds zero
      audit data so RBAC is untouched; screener still runs first; retrieval failure
      degrades to the pre-RAG prompt. (5) **Hand-rolled stateless JSON-RPC endpoint**
      (initialize/version negotiation, ping, tools/list, tools/call, 202 notifications,
      405 on GET) instead of the MCP Java SDK — the SDK's session/SSE machinery is exactly
      what this server doesn't use — and `permitAll` because the corpus is public repo docs
      (revisit trigger: non-public data entering the corpus). Tested at every seam
      (chunker, both stores, Voyage client via MockRestServiceServer, indexer increments,
      full MCP wire protocol via MockMvc); 90% gate held. Not exercised against the live
      Voyage API here (no key in this environment) — first real smoke: export
      `VOYAGE_API_KEY`, `docker compose up -d --build`, then the `claude mcp add` line above.

### Product/UI roadmap (portfolio presentation)
- [x] **Internationalization (i18n) — implemented, hand-rolled + JSON-driven.** The UI text is
      driven from per-language JSON dictionaries (`UI/public/i18n/<code>.json`) through a tiny
      no-framework runtime: a `TranslateService` (current-language signal, English bundled as the
      always-present fallback, other languages fetched once and cached in memory + an nginx
      `Cache-Control` on `/i18n/` since they only change per deploy), an impure `t` pipe
      (`{{ 'nav.dashboard' | t }}`, with `{token}` interpolation for counts), and a header
      **language switcher next to the avatar** offering the nine required languages — English,
      French, Spanish, Hindi, Gujarati, Punjabi, Chinese, Korean, Japanese (each shown in its own
      endonym) — plus an on-demand **Google Translate** option that lazily loads Google's Website
      Translator for anything not translated in-app (e.g. the long-form About prose, left to
      Google by design). The choice persists in localStorage and drives `<html lang>` (also helps
      Google detect the source page). Scope: nav, login, dashboard, chat, flashcards and profile
      chrome are translated; backend data values (entityType/action bucket keys) stay as-is.
      Verified live end-to-end (switched to Japanese: full UI + count-interpolated "338 件の一致",
      persisted across reload, `ja.json` served 200 from the image). Frontend-CI green (build,
      ESLint, Prettier, 49 unit tests). Two small dashboard fixes rode along: the "N matching
      entries" line is now a full-width panel heading divided from the two charts by a rule (it
      previously read ambiguously as a caption on the first chart), and confirmed the "Add demo
      logs" input+button stay hidden in PROD (the `/api/v1/meta/features` probe derives `demoData`
      from the LOCAL/DEV-only `DemoDataController` bean's presence).
- [ ] **Assistant + flashcards answer in the user's selected language; add more locales.** The UI
      chrome is translated (i18n above), but the LLM features still reply in English. Thread the
      current UI language (`TranslateService.lang`) into the chat + flashcard requests and have the
      system prompt instruct the model to answer in that language — grounding/allowlist/screener
      posture unchanged, only the output language. Also extend the switcher with new
      `public/i18n/<code>.json` dictionaries + `LANGUAGES` entries: **Arabic, Hebrew, Tamil, Telugu,
      Kannada, Malayalam, Marathi, Bangla (Bengali), Urdu** (Korean and Spanish already ship).
      Note: Arabic, Hebrew and Urdu are **RTL** — set `dir` on the document alongside `lang` and
      re-check the layout (sticky header, filter form, chat bubbles, the mobile card table) in RTL.
- [ ] **Mobile design round 2 — cut the scrolling (hamburger / drawers).** The responsive pass made
      everything usable on phones but *tall*: the nav, the stacked full-width filter form, and the
      card-per-row audit table mean a lot of vertical scrolling. Collapse the nav into a hamburger
      menu (the language switcher + avatar can live in it too), make the dashboard filters a
      collapsible "Filters" drawer/accordion instead of an always-expanded stacked form, and
      condense the stat charts on small screens. Re-verify with Playwright mobile emulation +
      Lighthouse.
- [ ] **Bug: header still shows "Login" after signing in via a returnUrl (avatar doesn't appear).**
      Repro: open a guarded page (e.g. Flashcards) while signed out → bounced to
      `/login?returnUrl=/flashcards` → sign in → land back on `/flashcards` (not `/profile`) → the
      header keeps showing "Login" instead of the avatar until a hard reload or visiting `/profile`.
      Root cause: `AuthService.isAuthenticated` is a computed over the `_profile` signal plus the
      *non-reactive* `storage.accessToken`, and `_profile` is only ever populated by the `/profile`
      page's `loadProfile()`. A login that doesn't land on `/profile` never sets `_profile`, so the
      computed doesn't re-run and the header stays stale. Fix: load the profile right after a
      successful login (in `demoLogin`/`consumeOAuthFragment`, after storing tokens) so `_profile`
      is set and the header updates reactively — and/or make auth reactivity token-driven (a signal
      set on token store/clear so `isAuthenticated` recomputes on login/logout regardless of route).
- [x] **GitHub-like dark theme UI restyle — implemented (PR #49).** One design-token layer in
      `styles.scss` (GitHub Primer dark palette as CSS custom properties: canvas `#0d1117`,
      borders `#30363d`, accent `#2f81f7`, plus text/status/button tokens) with base element
      defaults, hand-rolled with no UI framework. Every component (header/nav, home, login,
      profile, audit table + stat bars, assistant chat) converted from hardcoded light colors to
      the tokens — change a value in one place and it flows everywhere. Lint/prettier/unit
      tests/prod build green.
- [x] **Home page — implemented (PR #50).** Replaced the thin two-card home with data-driven
      portfolio content: a tech-stack overview grouped by area, six design decisions each with an
      explicit *Why* and *How* (event-driven audit, RBAC JWTs, statelessness, rate limiting,
      observability, the guarded LLM proxy) with a pointer to the ADRs, and a feature tour linking
      into the app. Rendered from typed arrays; specs assert the content and the why/how pairing.
- [x] **Flashcards feature — implemented.** `POST /api/v1/assistant/flashcards` generates a
      Q&A study deck about the app via the same Claude proxy, reusing the assistant seams rather
      than duplicating them: same `LlmClient` (server-side key, no auth headers forwarded) and
      the **same allowlist** — the role-scoped grounding block was extracted into a shared
      `AssistantContextBuilder.groundingContext(admin)` that both chat and the flashcard prompt
      compose, so a USER deck draws on docs + aggregate stats and an ADMIN deck additionally on
      recent rows (identical RBAC boundary). The model is asked for strict JSON; unreadable/empty
      replies become 503 and malformed cards are dropped so the UI never renders a blank. UI: a
      guarded `/flashcards` flip-card page with next/prev/shuffle and a 1..20 count. Tested at
      each seam (parse/guardrail/role gating, MockMvc e2e with the provider mocked, component
      nav/flip/shuffle); 90% coverage gate held. Reuse documented in the ADR-0009 addendum. Not
      run against the live Claude API here (no key) — same smoke path as the chat assistant.

#### Frontend polish — header nav
- [x] **Nav tabs render with inconsistent alignment on load — fixed via deterministic geometry.**
      The tabs' boxes were derived from text metrics (baseline-aligned links with `padding` +
      `align-items: center`), so their placement depended on font/zoom rounding per element.
      Rebuilt so every tab's box derives from the header's fixed 56px alone: `align-items:
      stretch` on the header and tab row, each tab a full-height `inline-flex` item with its
      content centered, and the active underline pinned to the header's bottom edge
      (GitHub-style) instead of floating under the text mid-header. Verified in the running app:
      all four tabs measure exactly top 0 / height 56 on every route.
- [x] **Reorder + restyle the header nav — implemented.** Tabs now read Audit, Assistant,
      Flashcards, About (feature tabs lead; the home page's tab follows, relabelled "About" by
      request since the page is portfolio/about content), and the Profile link is a circular
      avatar icon (person SVG, 32px, `aria-label="Profile"` so the Playwright e2e's
      `getByRole('link', { name: 'Profile' })` still matches) pushed to the far top-right with
      `margin-left: auto`; signed out, that same far-right slot shows the Sign in link instead.
      New unit tests assert the tab order and the avatar/Sign-in swap (mocking `AuthService`'s
      `isAuthenticated` signal); format/lint/47 unit tests/prod build all green, and both states
      verified visually in the dev server.

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
- [x] **Playwright E2E suite against the compose stack — implemented.** New top-level `e2e/`
      package (`@playwright/test`, Chromium, its own lockfile so the UI's `npm ci` stays lean) and
      an `E2E` workflow (`.github/workflows/e2e.yml`): `docker compose up -d --build` on the real
      top-level compose file (no CI-special profile), gate on both actuators + nginx serving the
      SPA, then run the suite. Four flows, nothing mocked: demo-form login lands on `/profile`
      (asserting the authed nav swap); **the demo login's `AuditEvent` is polled for through the
      details-contains filter until it appears as a LOGIN row** — the browser → nginx → Auth →
      Redpanda → Audit-consumer → Postgres path asserted as one system, the exact gap this item
      named; "Add demo logs" grows the pager total by ≥N (≥ not =, because the suite's own LOGIN
      events land concurrently); and an entity-type filter asserts every visible row matches *and*
      that the stats total equals the pager total (the same-Specification search/aggregate
      agreement, checked from the browser). Serial workers + one CI retry (traces kept on first
      failure) since the system is genuinely async; compose logs dump on failure and the HTML
      report uploads as an artifact. Deliberately not a required branch-protection check — its
      path filters only cover system-affecting paths, the same never-triggers asymmetry the
      Frontend CI item documented.
- [x] **API contract gate (openapi-diff) — implemented.** New `API contract` workflow
      (`.github/workflows/api-contract.yml`, PR-only — on a push to `main` there is no base
      contract to compare against): checks out the PR head *and* the base branch side-by-side,
      generates all four specs with `.github/scripts/generate-openapi-specs.sh` (boots each
      service on the LOCAL profile and fetches springdoc's `/v3/api-docs` — the spec comes from
      the running code, so it can't drift), then `openapitools/openapi-diff` fails the job on
      breaking changes only (`--fail-on-incompatible`; additive changes pass), with the diff
      rendered into the job summary. Both diffs always run so a PR breaking both contracts reports
      both at once. Two real findings along the way: (1) **Audit's `/v3/api-docs` had been 500ing
      in every environment** — the Anthropic SDK's classic (javax) `swagger-annotations` jar
      defines the same classes as springdoc's `swagger-annotations-jakarta`, and the older copy
      winning classloading broke springdoc with `NoSuchMethodError: Schema.$dynamicRef()`; fixed
      by excluding the duplicate (own PR, verified against the running compose stack). (2)
      openapi-diff's parser reads OpenAPI 3.0 but springdoc emits 3.1 by default — the script
      sets `SPRINGDOC_API_DOCS_VERSION=openapi_3_0` for the generated comparison specs only, so
      the running services keep serving 3.1. Whole pipeline validated locally end-to-end (boot →
      fetch → docker-run diff → "No differences") before it ever ran in CI.
- [x] **CD: versioned images to GHCR on merge to `main` — implemented.** New `CD` workflow
      (`.github/workflows/cd.yml`): on every merge to `main` (path-filtered to system-affecting
      paths, plus `workflow_dispatch`), a fail-fast-off matrix builds all three images (audit,
      auth — `Backend/` context matching their Dockerfiles' COPY layout; ui — `UI/` context) and
      pushes each to GHCR under `ghcr.io/<owner>/ai-sandbox/<name>` with three tags via
      `docker/metadata-action`: a generated SemVer `0.1.<run_number>` (honest CI-versioning,
      monotonic, until real release tags exist), `sha-<short>` (pins a build to its exact
      commit — the promote-by-digest handle), and `latest`. Auth is `GITHUB_TOKEN` with
      `packages: write` — no PAT to manage. The pull-instead-of-build variant is
      `docker-compose.ghcr.yml`, an override on the main compose file
      (`docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d --no-build`,
      `AI_SANDBOX_TAG=sha-<short>` to pin; `--no-build` documented in the header since the base
      file's `build:` sections still apply). `docs/deployment.md` §3/§7 flipped from planned to
      built for the publish half — deploy-to-a-live-env stays honestly `[planned]`, and one-time
      GHCR package visibility (public vs `docker login`) is called out in the override's header.
- [x] **Mutation testing (PIT), report-only — implemented.** `info.solidsoft.pitest` 1.19.0
      (1.15.0 fails on Gradle 9 — `reporting.baseDir` is gone) applied to all three modules from
      the root `build.gradle`, deliberately **not** wired into `check`: no `mutationThreshold`,
      so nothing gates yet — the same report-first/gate-later rollout the Trivy image scans used.
      A `Mutation testing` workflow (`.github/workflows/mutation.yml`, PRs touching Java/Gradle
      files + main + dispatch, not a required check) runs `pitest` on all modules, parses each
      `mutations.xml` into a job-summary score table, and uploads the HTML reports. The heavy
      Spring-context suites (`*IntegrationTest`, `*SecurityTest`) are excluded from PIT's runs —
      under per-mutant re-execution they dominate wall-clock without adding mutant-killing power
      over the focused unit tests (full local run: ~6.5 min for all three modules). **The
      baseline is the point**: common 96% / Auth 80% / **Audit 70%** detected — against a uniform
      90% *line* gate, exactly the "coverage executes code, assertions kill mutants" gap this
      item predicted; the survivor report is the shortlist for tightening tests before any gate
      is set. Numbers are from a real local run of every module, not projected.
- [x] **SBOM (syft) + image signing (cosign) — implemented.** Bolted onto the CD workflow (the
      publish point — signing CI-only throwaway images would prove nothing), not the Trivy jobs
      this item originally guessed at: after each image pushes, `cosign sign` signs the exact
      **digest** (`steps.push.outputs.digest`, not a mutable tag) **keyless** — `id-token: write`
      gives the workflow an OIDC identity that becomes the signing cert, recorded in the public
      Rekor transparency log, so there's no key to store, leak, or rotate; then
      `anchore/sbom-action` (syft) generates an SPDX SBOM from the pushed image, uploaded as a
      workflow artifact *and* attached to the image as a signed `cosign attest --type spdxjson`
      attestation, so the dependency inventory travels with the artifact it describes. The
      verify command (pinning `--certificate-identity-regexp` to this repo's `cd.yml` — the
      identity check is what makes keyless signing mean something) is documented in the workflow
      comment and `docs/deployment.md` §3.4, which flips to `[built]`; wiring verification into
      an actual deploy step stays honestly `[planned]` alongside the deploy half of CD. Verified
      like CD itself had to be: `cd.yml` only runs on push to `main`, so the signing/SBOM steps
      can't execute on their own PR — the first post-merge run is the proof, watched and fixed
      forward if red.

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
      held). Sub-part (3) **load & scale proof — DONE**: `docker-compose.scale.yml` runs Auth at
      2 replicas behind the UI's round-robin nginx (shared `AUTH_RSA_PRIVATE_KEY` from the host
      env, host port unpublished); a 20-step refresh-rotation chain through the proxy landed
      11/10 across the two replicas with every hop succeeding and a replayed consumed token
      rejected 401 — Redis-backed single-use across replicas, demonstrated not assumed. Doing it
      surfaced a real multi-replica bug: the JWKS `kid` was `UUID.randomUUID()` per process, so
      replicas sharing a key advertised different kids and Audit's kid-matched JWKS lookup
      rejected tokens minted by whichever replica it hadn't fetched from — fixed by deriving the
      kid from the RFC 7638 key thumbprint (same key → same kid, tested). The k6 suite then ran
      green against this 2-replica JWT-secured stack (new `-e TOKEN=` support in the scripts).
      Sub-part (2) **no SPOF** stays open and infra-blocked: Redis is single-replica
      (Sentinel/managed HA next), Kafka is a single-broker Redpanda dev container (multi-broker
      next), Postgres is single (read-replica/HA next).
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
- [x] **Observability is configured but unproven end-to-end — now proven, and the proving found
      real breaks.** Ran the k6 scripts against the full compose stack (JWT-secured DEV profile,
      Auth at 2 replicas) and captured the Grafana overview dashboard mid-load into the README
      (`docs/images/grafana-overview-load.png`): request-rate bursts, per-endpoint p95/p99,
      live Loki logs, empty 5xx panel. The exercise surfaced and fixed four real bugs that config
      review had passed: (1) loki4j 2.x silently ignores the 1.x `<format><label>` config — every
      log line shipped as `app=default` and the dashboard's Logs panel showed "No data"; (2)
      Prometheus scraped `host.docker.internal` — broke when auth's host port went away and could
      never see a second replica; now `prometheus-compose.yml` with Docker-DNS `dns_sd_configs`
      (all replicas discovered, verified 3 targets up); (3) the `@Async` audit publish dropped
      the trace context (see the tracing item); (4) the rate limiter's 429 body corrupted
      already-committed 200 responses under same-key GET contention — `{...json}{...429 json}`
      on the wire, seen on ~70% of contended stats calls, fixed with a committed/resetBuffer
      guard in both services' handlers (the CI load job couldn't see it: it runs search-stats
      with the limiter off, and rate-limit.js checks status codes, not body integrity).
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
      to test), both compose files + Tempo config YAML-valid. **Follow-up: confirmed live, after
      a fix.** Running the stack and querying Tempo's API showed the producer/consumer halves
      linked correctly — but detached from the originating HTTP trace: the fire-and-forget
      `@Async` publish dropped the active Observation on the executor hop, so login → Kafka →
      audit-persist rendered as two disconnected traces. A `ContextPropagatingTaskDecorator`
      bean (Boot applies it to the `applicationTaskExecutor` backing `@Async`) closed it;
      verified via Tempo: one trace containing auth's `http post /auth/login` server span, its
      `audit.events send` producer span, and audit's `audit.events receive` consumer span.
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
