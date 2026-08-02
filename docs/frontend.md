# 🎨 Frontend (UI)

_Part of the [ask-app](../README.md) documentation._

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
- **DB-backed feature flags** — a `FeatureFlagService` (signal store) loads
  `/audit-api/api/v1/meta/flags` and gates which major features render: the nav rail
  filters on it, a `featureFlagGuard` protects the routes, and the assistant's
  voice/hints controls `@if` on it. Load timing: an `APP_INITIALIZER` resolves flags
  before first paint when a session already exists (reload), and an auth-driven
  `effect` loads them right after a fresh login. It **fails open** — until flags load
  (login page, transient error, the moment after login) every flag reads as enabled,
  so the app never bricks; the flags curate the UI, they aren't a security control
  (authorization stays server-side). See [ADR-0015](../Backend/docs/adr/0015-ui-feature-flags.md).
- Shipped as a static bundle by nginx (immutable cache headers, SPA fallback);
  GA4 page views and Sentry errors fire only when their IDs/DSNs are configured.
