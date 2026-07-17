# How To — Redis

_Set up, access, and inspect Redis (Redis Insight, CLI, the 2-replica proof) at every level of
the app — LOCAL, DEV, and PROD._

**Jump to:** [LOCAL](#local-bare-gradle-services) · [DEV](#dev-full-compose-stack) ·
[PROD](#prod) · [Good to know](#good-to-know)

Redis is Auth's shared refresh-token store — the piece that makes Auth stateless and
horizontally scalable. Tokens are single-use via atomic `GETDEL`, so a refresh consumed on one
replica is dead on all of them
([ADR-0007](../../Backend/docs/adr/0007-redis-refresh-token-store-for-statelessness.md)). Keys
are short-lived and re-mintable, so Redis runs with **no persistence** (no RDB/AOF).

## At each level

| Level | Store | GUI |
|---|---|---|
| LOCAL (bare Gradle) | **in-memory map** (no Redis needed) | — |
| DEV (compose stack) | `redis:7-alpine`, `localhost:6379`, no auth | Redis Insight: http://localhost:5540 |
| PROD | same; no published port | none (Insight isn't deployed) |

### LOCAL (bare Gradle services)

The `RefreshTokenStore` is a **Strategy**: the default is an in-process map — perfectly fine for
one Auth instance, zero setup. To exercise the Redis path locally:

```bash
docker run --rm -p 6379:6379 redis:7-alpine
AUTH_REFRESH_TOKEN_STORE=redis REDIS_HOST=localhost ./gradlew :Auth:bootRun
```

### DEV (full compose stack)

Already wired (`AUTH_REFRESH_TOKEN_STORE=redis`, `REDIS_HOST=redis`).

- **Redis Insight** (http://localhost:5540): add a database — host `redis`, port `6379`, no
  auth — then browse Auth's refresh-token keys (log in on the UI first so some exist).
- **CLI**:

```bash
redis-cli -h localhost -p 6379 ping          # → PONG
docker compose exec redis redis-cli KEYS '*' # inspect keys (dev only!)
```

**See the point of it**: `docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d`
runs 2 Auth replicas behind round-robin nginx — a refresh chain hops replicas and still works,
and replaying a consumed token is rejected (`GETDEL` single-use across pods).

### PROD

No published port; access over SSH:

```bash
gcloud compute ssh ask-app-vm --zone=us-east1-b
docker exec askapp-redis redis-cli ping
docker exec askapp-redis redis-cli --scan --count 100   # key names only — values are tokens
```

## Good to know

- Auth **degrades safely** if Redis is down: refresh fails, login still works.
- The rate limiter is *not* in Redis, deliberately — it's a per-pod thread-interrupt dedup, not
  a counter ([ADR-0007](../../Backend/docs/adr/0007-redis-refresh-token-store-for-statelessness.md)
  corrects the earlier framing).
