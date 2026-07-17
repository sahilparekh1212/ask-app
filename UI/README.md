# ask-app UI

_Developer guide to the ask-app frontend — what the SPA does, the design decisions behind it,
and how to run it._

**Contents:** [Routes](#routes) · [Design decisions](#design-decisions--what--how) ·
[Run it](#run-it)

The Angular SPA fronting the ask-app backend: sign in (demo login or Google OAuth), browse
the audit dashboard, chat with the guarded, RAG-grounded LLM assistant. Standalone components,
signals, no UI framework. The chrome is a VS Code-style shell: activity rail + contextual
"on this page" sidebar with scroll-spy. Production telemetry is built in: **Google Analytics 4**
reports SPA route changes as page views and **Sentry** captures user-facing errors — both
config-driven and fully dormant when their key/DSN is empty.

## Routes

| Route                       | What it does                                                             | Auth    |
| --------------------------- | ------------------------------------------------------------------------ | ------- |
| `/login`, `/login/callback` | Demo login form, Google OAuth fragment handoff                           | public  |
| `/observability`            | Paginated/sorted/filtered audit table, KPI cards, events-over-time chart | guarded |
| `/chat`                     | Chat over the server-side Claude proxy (RAG-grounded)                    | guarded |
| `/profile`                  | `/auth/me` profile view                                                  | guarded |

## Design decisions — what & how

| Concept — _what & why_                                                                                                         | How — _mechanism_                                                                                                                                                                                                                                                                                                            |
| ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **SPA, not a microfrontend federation** — one team, two features: the shell/remote cost is immediate, every MFE benefit latent | One **Angular 21** app of standalone components with lazy routes ([ADR-0008](../Backend/docs/adr/0008-no-microfrontend-split.md))                                                                                                                                                                                            |
| **Reactive state without a store library**                                                                                     | **Signals** + `computed`; RxJS only at the HTTP edge                                                                                                                                                                                                                                                                         |
| **Stateless client auth** — bearer token only, invisible expiry recovery                                                       | Functional **HTTP interceptor** (attaches `Bearer`, single silent refresh-and-retry on 401), route guard with `returnUrl`, OAuth `#access_token` fragment consumed then wiped via `history.replaceState`; token in localStorage (documented tradeoff vs httpOnly cookies). All in [`src/app/core/auth/`](src/app/core/auth/) |
| **No CORS, in prod or dev**                                                                                                    | Both builds call relative `/auth-api` + `/audit-api`; nginx same-origin reverse-proxies in prod ([`nginx.conf`](nginx.conf), incl. `X-Forwarded-*` for OAuth), [`proxy.config.js`](proxy.config.js) does the same under `ng serve`                                                                                           |
| **Theming as one source of truth**                                                                                             | One CSS custom-property design-token layer in [`src/styles.scss`](src/styles.scss) (Primer dark palette), no framework                                                                                                                                                                                                       |
| **i18n** — text from data, not hardcoded                                                                                       | Hand-rolled `TranslateService` + `t` pipe fed by `public/i18n/<code>.json`; currently **English-only by decision**, runtime kept so a language is one JSON file away                                                                                                                                                         |
| **Analytics / error monitoring**                                                                                               | **GA4** (`gtag.js`, typed, page_view per route change) and **Sentry**; both fully disabled when their key/DSN is empty                                                                                                                                                                                                       |
| **Dependency-free data viz**                                                                                                   | CSS-only stat bars + hand-built column chart, sharing the design tokens                                                                                                                                                                                                                                                      |
| **CI**                                                                                                                         | `frontend-ci.yml`: Prettier, angular-eslint, prod build, headless Karma/Jasmine on every `UI/**` PR                                                                                                                                                                                                                          |
| **E2E where the seams are**                                                                                                    | **Playwright** ([`e2e/`](../e2e/)) drives the real compose stack — login through nginx, Kafka-produced audit rows appearing in the table                                                                                                                                                                                     |
| **Container**                                                                                                                  | Two-stage Docker build (Node 22 → `nginx:1.28-alpine`): SPA fallback, immutable cache on hashed bundles, `no-cache` on `index.html`                                                                                                                                                                                          |

## Run it

**Full stack, no Node needed:** from `Backend/`, `docker compose up --build` →
<http://localhost:4200> (demo login `demo` / `demo`).

**Dev server:** `npm ci && npm start` — same-origin `/auth-api` + `/audit-api` are proxied to
`:8085`/`:8083` by [`proxy.config.js`](proxy.config.js), so both services must be running
(compose stack or `gradlew bootRun`).

```bash
npm run build        # prod build → dist/ask-app-ui/browser
npm run test:ci      # headless unit tests (what Frontend CI runs)
npm run lint         # angular-eslint
npm run format:check # what CI enforces
```
