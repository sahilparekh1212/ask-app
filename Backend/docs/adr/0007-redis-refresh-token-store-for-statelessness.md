# ADR-0007: Externalize the refresh-token store to Redis; keep the rate limiter per-pod

**Status:** Accepted. Partially supersedes [ADR-0002](0002-in-memory-rate-limit-and-refresh-store.md).

## Context

[ADR-0002](0002-in-memory-rate-limit-and-refresh-store.md) backed both the refresh-token store and
the rate limiter with in-process `ConcurrentHashMap`s and named the consequence plainly: **this does
not scale past one replica.** With HPA manifests in place (`openshift/*/hpa.yaml`) but pinned to a
single replica, that in-memory state was the last thing keeping Auth from actually running >1 pod.
This ADR records the decision to remove that blocker.

Looking at the two "stateful" pieces up close, they are not the same kind of state:

- **Refresh-token store** (`TokenService`): a genuine shared-state problem. A token issued by pod A
  lives only in pod A's heap, so a refresh request load-balanced to pod B silently fails. This *must*
  be externalized to scale.
- **Rate limiter** (`ActiveRequestRegistry` / `ActiveRequest` in `:common`): despite its name, this
  is **not** a counting/quota limiter. It enforces "one in-flight request per (user, method, path),
  newest wins" by holding the worker `Thread` and calling `Thread.interrupt()` on the superseded
  request so its blocking I/O aborts. The state it holds is a live thread reference — inherently
  local to one JVM. You cannot interrupt a thread that lives in another pod, so there is nothing
  meaningful to move to Redis. (ADR-0002's "effective limit becomes replicas × configured limit"
  framing was written as if this were a counter; it isn't — see Consequences.)

## Decision

1. **Externalize the refresh-token store to Redis.** Introduce a `RefreshTokenStore` interface with
   two implementations selected by `auth.refresh-token.store`:
   - `InMemoryRefreshTokenStore` (default, `matchIfMissing=true`) — unchanged single-replica / dev /
     test behavior, still relying on atomic `ConcurrentHashMap.remove`.
   - `RedisRefreshTokenStore` (`=redis`) — stores each token as typed JSON with a Redis key TTL equal
     to the refresh-token lifetime, and consumes via **`GETDEL`** (`ValueOperations.getAndDelete`), a
     single atomic fetch-and-delete. This preserves the single-use guarantee *across replicas* —
     strictly stronger than the in-memory store, which only guaranteed it within one JVM.

2. **Keep the rate limiter per-pod, by design.** Each pod independently guards against a user
   double-firing the same request *against that pod*. This is correct, not a scaling bug — see below.

3. **Reuse the already-externalizable signing key.** Horizontal scaling also requires every pod to
   sign with the same RSA key; that was already solved via `AUTH_RSA_PRIVATE_KEY` (see
   [ADR-0001](0001-rsa-jwt-signing.md) and `JwtConfig`). No new work — just a precondition this ADR
   depends on and the OpenShift manifests now assert.

With (1) and (3) satisfied, Auth's deployment and HPA are moved to **≥2 replicas**
(`openshift/auth/`), and a Redis Deployment/Service is added (`openshift/redis/`) plus a `redis`
service in `docker-compose.yml`. Audit was already stateless (external Postgres; Kafka consumer
idempotent by `eventId`), so it moves to ≥2 replicas as well.

## Alternatives considered

- **Externalize the rate limiter to Redis too** (for consistency with ADR-0002's wording). Rejected:
  it would be a category error. A Redis counter is a *different mechanism* (a distributed quota),
  not a remote version of the thread-interrupt dedup we have. If cross-pod superseding is ever
  wanted, the right design is a Redis pub/sub "cancel key K" broadcast that each pod subscribes to
  and applies to its *local* threads — a deliberate feature, not a store swap. Named as future work,
  not built.
- **Make the whole thing Redis-only, no in-memory path.** Rejected: it would force every local run
  and every unit test to stand up Redis. The property-selected default keeps dev/test friction at
  zero (the Redis starter's Lettuce client connects lazily, so its presence on the classpath is
  inert when the store is in-memory) while production opts in explicitly.
- **A database table for refresh tokens.** Rejected for the same access-pattern reason as ADR-0002:
  short-lived, high-churn key/value with free TTL expiry is exactly Redis's shape, not a relational
  table's.

## Consequences

- **Auth is now horizontally scalable.** Refresh works regardless of which pod a request lands on;
  the RSA key is shared; ≥2 replicas run in both compose (`--scale auth=2`) and OpenShift.
- **The rate limiter's per-pod scope is a genuine, accepted limitation.** With a load balancer, two
  near-simultaneous identical requests from one user can land on different pods and neither will
  cancel the other. This is acceptable: the feature exists to abort *obviously* superseded in-flight
  work (e.g. type-ahead search on one connection), and cross-pod thread interruption isn't possible
  without the pub/sub design above. It is **not** the "limit × replicas" quota inflation ADR-0002
  implied — there is no quota being multiplied, because nothing is being counted.
- **Redis is a new dependency and, as deployed here, a SPOF** (single replica, no persistence —
  fine, since tokens are re-mintable). Redis Sentinel / managed HA Redis is the next step and stays
  tracked under the "remove single points of failure" half of the statelessness TODO item, alongside
  multi-broker Kafka and DB HA.
- **Still unproven at load with >1 live replica.** The k6 scripts exist and pass single-instance;
  running them against a 2-replica stack to confirm refresh survives a pod bounce needs a live
  cluster/Docker and remains an infra follow-up.
