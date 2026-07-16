# ask-app UI

The Angular SPA fronting the ask-app backend: sign in (demo login or Google OAuth), browse
the audit dashboard, and chat with the guarded, RAG-grounded LLM assistant.
Standalone components, signals, no UI framework — the dark theme is a single hand-rolled
design-token layer in [`src/styles.scss`](src/styles.scss).

One deliberately boring architecture decision up front: this is **one SPA, not a
microfrontend federation** — see
[ADR-0008](../Backend/docs/adr/0008-no-microfrontend-split.md) for why, and what would have
to change to revisit that.

## What's inside

| Route                       | What it does                                                                                             | Auth    |
| --------------------------- | -------------------------------------------------------------------------------------------------------- | ------- |
| `/about`                    | About — tech stack, design decisions, feature tour                                                       | public  |
| `/login`, `/login/callback` | Demo login form, Google OAuth fragment handoff                                                           | public  |
| `/observability`            | Server-side paginated/sorted/filtered audit table, KPI cards, events-over-time chart, CSS-only stat bars | guarded |
| `/chat`                     | Chat over the server-side Claude proxy (RAG-grounded)                                                    | guarded |
| `/profile`                  | `/auth/me` profile view (avatar icon, top-right)                                                         | guarded |

(The old `/audit`, `/assistant`, and `/flashcards` paths redirect to `/observability` and `/chat`
so bookmarks keep working — flashcards was removed and folded into the chat assistant.) The chrome is a **VS Code-style nav shell**: an activity rail, a
contextual "on this page" sidebar with scroll-spy highlighting, and a top bar.

The auth plumbing lives in [`src/app/core/auth/`](src/app/core/auth/): a functional HTTP
interceptor that attaches `Authorization: Bearer` (our APIs only) and does a single silent
refresh-and-retry on 401, a route guard with `returnUrl`, the OAuth `#access_token` fragment
consumption (followed by `history.replaceState` so tokens don't linger in history), and a
localStorage token store — an explicit tradeoff vs httpOnly cookies, documented where it's
implemented.

## Concepts & design decisions — what & how

Each idea this SPA demonstrates, as **what** (the decision and why) and **how** (the concrete
Angular/web mechanism):

| Concept — _what & why_                                                                                                                         | How — _technology / mechanism_                                                                                                                                                                                                                                                                                                                                                                                          |
| ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **SPA, not a microfrontend federation** — one team, two features: the shell/remote-loading cost is immediate while every MFE benefit is latent | A single **Angular 21** app of **standalone components** with lazy routes for code-splitting ([ADR-0008](../Backend/docs/adr/0008-no-microfrontend-split.md))                                                                                                                                                                                                                                                           |
| **Reactive state without a store library** — local, fine-grained reactivity beats a global store for an app this size                          | Angular **signals** + `computed` for state; **RxJS** only at the HTTP edge; the shell's scroll-spy is a signal driven by a scroll listener                                                                                                                                                                                                                                                                              |
| **Stateless client auth** — the SPA holds no session, just a bearer token, and recovers from expiry invisibly                                  | A **functional HTTP interceptor** attaches `Authorization: Bearer` (our APIs only) and does a single **silent refresh-and-retry on 401**; a **functional route guard** with `returnUrl`; the OAuth `#access_token` fragment is consumed then wiped with `history.replaceState`. Token in **localStorage** — a documented tradeoff vs httpOnly cookies                                                                   |
| **No CORS, in prod or dev** — the SPA shouldn't hardcode a backend host or need cross-origin                                                   | Both builds call relative `/auth-api` + `/audit-api` paths. nginx **same-origin reverse-proxies** them into the compose network in production; [`proxy.config.js`](proxy.config.js) does the same under `ng serve` (forwarding to `:8085`/`:8083`, prefix stripped) — so neither the deployed SPA nor the dev server depends on CORS. nginx also sets `X-Forwarded-*` so Google OAuth's redirect URI survives the proxy |
| **Theming as one source of truth** — change a colour once, it flows everywhere; no framework weight                                            | A single **CSS custom-property design-token** layer in [`src/styles.scss`](src/styles.scss) (GitHub Primer dark palette), hand-rolled — no UI framework                                                                                                                                                                                                                                                                 |
| **Internationalization (i18n)** — text driven from data, not hardcoded, so a language is one file away                                         | A hand-rolled runtime: a `TranslateService` (current-language signal, English bundled as fallback) + an impure `t` pipe with `{token}` interpolation, fed by `public/i18n/<code>.json`. **Currently English-only by decision** — the runtime + header switcher (and a Google Translate escape hatch) are kept, so re-adding a language is dropping one JSON file + one `LANGUAGES` entry                                |
| **Product analytics** — know which features get used, without a wrapper lib                                                                    | **Google Analytics 4** (`gtag.js`, hand-rolled + typed) reporting SPA route changes as `page_view`s; a no-op when the Measurement ID is empty (dev/forks)                                                                                                                                                                                                                                                               |
| **Error & performance monitoring** — see real user-facing errors and web-vitals from production                                                | **Sentry** (browser SDK) behind a publishable DSN; disabled entirely when the DSN is empty (falls back to the default Angular `ErrorHandler`)                                                                                                                                                                                                                                                                           |
| **Dependency-free data viz** — a dashboard shouldn't pull a charting library for bar/column charts                                             | CSS-only stat bars + a hand-built SVG-free events-over-time column chart, sharing the same design tokens                                                                                                                                                                                                                                                                                                                |
| **CI** — every UI change is format/lint/build/test-gated                                                                                       | **Frontend CI** (`frontend-ci.yml`): Prettier check, angular-eslint, prod build, headless Karma/Jasmine unit tests on every `UI/**` PR                                                                                                                                                                                                                                                                                  |
| **End-to-end where the seams are** — the interesting failures live between the SPA and the system, not inside it                               | **Playwright** in the top-level [`e2e/`](../e2e/) package drives the real compose stack (login through nginx, Kafka-produced audit rows appearing in the table)                                                                                                                                                                                                                                                         |
| **Container** — serve a static bundle, cache it correctly                                                                                      | Two-stage **Docker** build (Node 22 build → `nginx:1.28-alpine`): SPA fallback, immutable cache headers on content-hashed bundles, `no-cache` on `index.html`                                                                                                                                                                                                                                                           |

## Run it

**Against the full Docker stack (no Node needed):** from `Backend/`,
`docker compose up --build` serves the production bundle through nginx at
<http://localhost:4200> (demo login: `demo` / `demo`).

**Dev server with live reload:**

```bash
npm ci
npm start            # ng serve on http://localhost:4200
```

The dev build ([`environment.development.ts`](src/environments/environment.development.ts)) uses
the same same-origin `/auth-api` and `/audit-api` paths as production. Under `ng serve` those are
forwarded by [`proxy.config.js`](proxy.config.js) to Auth on `:8085` and Audit on `:8083` (with
the prefix stripped, just like [`nginx.conf`](nginx.conf) does in the container), so both services
must be running (the compose stack works, or `gradlew bootRun` per the backend README). Because
the browser only ever talks to `http://localhost:4200`, dev now uses the same single-origin model
as production and no longer depends on the backends' CORS allowance. In production nginx proxies
the same prefixes inside the compose network — see [`nginx.conf`](nginx.conf) for the rules
(including the `X-Forwarded-*` headers Google OAuth needs to survive the proxy).

## Scripts

```bash
npm start            # dev server
npm run build        # production build → dist/ask-app-ui/browser
npm test             # Karma/Jasmine, watch mode
npm run test:ci      # headless single run (what Frontend CI runs)
npm run lint         # angular-eslint
npm run format       # prettier --write
npm run format:check # what CI enforces
```

`Frontend CI` (`.github/workflows/frontend-ci.yml`) runs format check, lint, prod build, and
the headless unit tests on every PR touching `UI/**`.

## Testing

Unit/component specs sit next to their sources (`*.spec.ts`) and run under Karma/Jasmine with
a CI-safe `ChromeHeadlessNoSandbox` launcher. Browser end-to-end tests are deliberately **not**
here — the Playwright suite in the top-level [`e2e/`](../e2e/) package runs against the real
compose stack (login through nginx, Kafka-produced audit rows appearing in the table), because
the interesting failures live in the seams between the SPA and the system, not inside the SPA.

## Container

[`Dockerfile`](Dockerfile) is a two-stage build: `npm ci` + prod build on Node 22 (matching
Frontend CI), then `nginx:1.28-alpine` serving the bundle with SPA fallback, immutable cache
headers on the content-hashed bundles, `no-cache` on `index.html`, and the two API reverse
proxies described above. The compose `ui` service publishes it on host `:4200`.
