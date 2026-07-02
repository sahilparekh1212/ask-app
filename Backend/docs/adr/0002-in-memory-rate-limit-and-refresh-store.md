# ADR-0002: In-memory store for rate limiting & refresh tokens over Redis

**Status:** Accepted (with a known, tracked expiry condition)

## Context

Two pieces of per-request state need somewhere to live: the rate limiter's active-request
registry (`ActiveRequestRegistry`, one entry per in-flight `userId+method+path`) and Auth's
refresh-token store (`TokenService`, one entry per issued refresh token). Both are naturally
key-value, short-lived, and read/written on the hot path of every request.

## Decision

Back both with `ConcurrentHashMap` in process memory, not Redis or a database. The rate limiter
needs lock-free, sub-millisecond reads on every request; a network round-trip to an external
store would add latency to the one thing the limiter exists to protect. The refresh-token store
followed the same reasoning for consistency and to avoid standing up infrastructure (Redis) this
single-instance portfolio deployment doesn't otherwise need.

## Alternatives considered

- **Redis for both.** The textbook answer once you have >1 replica — shared state, atomic
  compare-and-delete for single-use refresh tokens, TTL-based expiry for free. Rejected *for now*
  because it adds a stateful dependency (another SPOF, another thing to run in `docker compose`)
  for a system that currently runs one replica per service.
- **A database table.** Same durability benefit as Redis, worse fit for the access pattern (the
  rate limiter's registry churns on every request; a DB round-trip per request is the wrong
  latency profile for a component whose entire job is protecting against request storms).

## Consequences

- **This does not scale past one replica.** A second Auth pod has its own refresh-token map, so a
  token issued by pod A won't be found by pod B — refresh silently fails depending on which pod
  the retry lands on. Same story for the rate limiter: two pods each think they're the only one
  enforcing the per-key limit, so the *effective* limit becomes (replicas × configured limit),
  not the configured limit.
- **Nothing survives a restart.** Every issued refresh token is invalidated on deploy/crash;
  every in-flight request's rate-limit bookkeeping is lost (harmless — it just resets to zero
  active requests, which is the safe direction to fail in).
- This is the single most-cited blocker to calling the system "stateless" or "horizontally
  scalable" as-is. It's tracked explicitly in `TODO.md` ("Auth refresh-token store is in-memory
  only", "Make services stateless, remove single points of failure, then handle load & scale")
  rather than silently accepted — the fix is externalizing both to Redis. Note this is about
  *scaling*, not correctness: `ConcurrentHashMap.remove(key)` is already atomic per key, so
  single-use refresh-token consumption has no race today even under concurrent requests for the
  same token (proven under real thread contention in
  `TokenServiceTest.consumeRefreshToken_isSingleUseUnderConcurrentRacingConsumers` — see the
  "Handle concurrency explicitly" TODO item). Redis would still be needed for a *second replica*
  to see the same token store, just not to fix a single-process race that doesn't exist.
