# ADR-0005: No API gateway / BFF (yet) — the SPA calls each service directly

**Status:** Accepted (deliberately not built — this ADR is the "articulate it" half of the TODO
item, not a decision to never build one)

## Context

With two backend services on two ports (Auth `:8085`, Audit `:8083`) and an Angular SPA planned,
the SPA needs *some* way to reach both. The two live options are: call each service directly
from the browser (what CORS was added for), or put a gateway/BFF in front of both so the SPA
talks to one origin.

## Decision

The SPA calls Auth and Audit directly, each on its own port, with CORS configured per service
(`CORS_ALLOWED_ORIGINS`, see `SecurityConfig` in both). No gateway exists yet.

With exactly two services and no cross-service composition need (the SPA's audit dashboard reads
straight from Audit's `/search`/`/stats`; its auth screens read straight from Auth's `/auth/me`
— neither view needs data stitched from both), a gateway would be pure indirection today: an
extra network hop, an extra deployable, an extra thing to keep in sync with both services' routes,
in exchange for zero composition benefit. CORS already solves the actual problem (the browser
refusing cross-origin calls) at a fraction of the operational cost.

## What would change the calculus

A gateway becomes worth its cost once any of these show up, roughly in the order they'd likely
happen here:

1. **A third service** whose data a single UI view needs alongside Auth/Audit — the gateway would
   compose responses server-side (BFF) instead of the SPA firing N parallel calls and merging
   client-side.
2. **Cross-cutting concerns that don't belong per-service** — today CORS, rate limiting, and JWT
   validation are each duplicated (well, CORS and rate-limiting logic are already shared via
   `:common`, JWT validation is inherent to each resource server) across Auth/Audit. A gateway is
   the natural place to centralize this instead of re-adding it to every future service.
3. **The microfrontend split actually gets built** (see the `UI/` TODO items) — `shell` +
   `auth-mfe` + `audit-mfe` each independently calling two backend origins is exactly the kind of
   network-config sprawl a BFF cleans up; right now there's no UI at all, so there's nothing to
   simplify yet.

## Alternatives considered

- **Build a minimal gateway now** (Spring Cloud Gateway, or even an nginx reverse proxy in the
  planned UI's container). Rejected for now — with two services and no composition need, this is
  solving a problem the system doesn't have yet, and it's easy to add later without touching
  either service's business logic (CORS could even stay as a fallback for direct access during
  the transition).
- **A shared `:common`-style config module for CORS/rate-limiting instead of a network gateway.**
  This is actually *already* the direction taken — the `:common` Gradle module (see `TODO.md`'s
  "Duplicated `ratelimit/` package" item) — code-level sharing solved the duplication half of what
  a gateway would centralize, without adding a network hop.

## Consequences

- The SPA's HTTP client needs two base URLs (Auth, Audit) instead of one — a real but small cost,
  configured once in the frontend's environment file when it's built.
- Each service independently enforces CORS, which is currently fine (both read from the same
  `CORS_ALLOWED_ORIGINS`-shaped config) but would need to be kept in sync by hand if origins ever
  diverge per service.
- If a third service or the microfrontend split arrives, revisit this ADR rather than
  accumulating duplicated cross-cutting config a third and fourth time.
