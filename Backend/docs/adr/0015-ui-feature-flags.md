# ADR-0015: DB-backed UI feature flags

**Status:** Accepted

## Context

The SPA had no way to turn a UI feature on or off per deployment. The closest thing was
`GET /api/v1/meta/features` ([`MetaController`](../../Audit/src/main/java/com/askapp/audit/controller/MetaController.java)),
whose single `demoData` flag is derived from a bean's presence — a *capability* probe ("does this
backend have the demo generator?"), not an operator switch. To dark-launch a feature, hide one that
isn't ready, or curate what a given deployment shows, we want to flip a switch **without a redeploy**,
and have the Angular client read those switches at startup.

The four major user-facing features are the natural granularity: the **chat** assistant, **voice**
chat (dictation / read-aloud / hands-free), the AI **hints** popover, and the **observability**
dashboard.

## Decision — a `feature_flags` table the SPA reads, seeded ON, flipped in the DB

Add a `feature_flags` table (one row per feature: `flag_key`, `enabled`, `description` + the standard
audit columns), created and **seeded ON** by Liquibase changeset `009-create-feature-flags`. A new
`GET /api/v1/meta/flags`
([`FeatureFlagController`](../../Audit/src/main/java/com/askapp/audit/controller/FeatureFlagController.java)
→ `FeatureFlagService` → `FeatureFlagRepository`) returns the whole set. The Angular
`FeatureFlagService` loads it once and gates features three ways: nav-rail filtering, a
`featureFlagGuard` on the routes, and `@if` in the assistant template (AND-ed with the existing
browser-capability checks for voice).

**Read-only from the app.** There is no write endpoint and no admin UI: flags are flipped directly in
the database (SQL / Adminer) or with a follow-up Liquibase changeset. This keeps the surface small and
avoids an authz story for "who can toggle" — the flags are a deployment/curation tool, not a per-user
control.

### Response is a list, not a map or new `FeaturesResponse` fields

`GET /meta/flags` returns `[{key, enabled, description}]`. The OpenAPI schema is then fixed regardless
of how many flags exist, so adding or removing a flag **row** never changes the API contract — the
`api-contract` CI check always sees an additive/no-op change. Widening the existing typed
`FeaturesResponse` record field-by-field, by contrast, would churn the schema on every new flag. The
existing `/features` endpoint is left untouched.

### Fail-open, and authenticated

The endpoint is **authenticated** (parity with `/features`; every gated feature already lives behind
login). The client **fails open**: until the flags have loaded — the login page, a transient error, or
the brief window right after a fresh login — every flag reads as enabled. A missing flag value must
never brick the app, and these flags are curation, not security (real authorization stays server-side
via OAuth2 + `@PreAuthorize`). Load timing: `app.config`'s initializer resolves flags before first
paint when a session already exists (reload case); on a fresh login an auth-driven `effect` loads them
the moment the user is authenticated.

### Seeded in Liquibase, not a Java runner

The four rows are static and known at author time, so they belong in the versioned changelog (runs
identically on H2 and Postgres, tracked in `DATABASECHANGELOG`) — unlike the *runtime-generated*
`security_master` seed, which is a Java `ApplicationRunner`.

### No caching

The table is a handful of rows read once per session; a plain repository read per call is trivial.
Spring Cache is not used anywhere in the Audit service, and this feature doesn't justify introducing
it.

## Alternatives considered

- **Config/env-driven flags (properties per deployment).** Rejected: changing one needs a redeploy;
  the whole point is to flip a feature live.
- **A feature-flag library (Togglz / Unleash / LaunchDarkly).** Rejected: heavyweight for four boolean
  switches on a portfolio app; a table + a GET is enough and stays dependency-free.
- **Extend `/meta/features` / widen `FeaturesResponse`.** Rejected: churns the OpenAPI contract per
  flag and conflates a config-derived capability probe with operator switches.
- **An admin write endpoint / toggle UI.** Deferred: adds an authz surface and E2E scope for no
  current need — flipping a DB row covers the use case.
- **Fail-closed.** Rejected: a transient error or the pre-load window would hide working features; the
  downside of fail-open (a briefly-visible feature) is harmless since flags aren't a security control.

## Consequences

- Flipping a feature is now a one-row DB update (`UPDATE feature_flags SET enabled=false WHERE
  flag_key='voice'`), effective on the next SPA load — no rebuild, no redeploy.
- With all flags ON (the seeded default) the UI behaves exactly as before, so existing Playwright E2E
  is unaffected.
- Disabling the **core** `chat` feature is handled: the guard redirects to the first enabled major
  route (`/observability`), final fallback `/profile`, so the app never dead-ends.
- Adding a future feature means: a seed row, a `flagKey` on its nav entry / route guard / template
  `@if`, and (optionally) a docs line — the mechanism generalizes.
