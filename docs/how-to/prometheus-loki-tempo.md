# How To — Prometheus, Loki & Tempo

_Set up and reach the three telemetry backends directly at every level of the app — LOCAL, DEV,
and PROD._

**Jump to:** [LOCAL](#local-bare-gradle-services) · [DEV](#dev-full-compose-stack) ·
[PROD](#prod) · [Good to know](#good-to-know)

**Prometheus** scrapes Micrometer metrics from `/actuator/prometheus`, **Loki** receives
structured logs pushed by logback, **Tempo** receives OTLP traces; all three are usually
consumed *through* [Grafana](grafana.md). Telemetry is active under **DEV and above** — the
LOCAL profile stays console-only.

## At each level

| Level | Prometheus | Loki | Tempo |
|---|---|---|---|
| LOCAL (monitoring compose) | http://localhost:9090 | http://localhost:3100 | http://localhost:3200 (OTLP in: 4318) |
| DEV (full compose stack) | http://localhost:9090 | http://localhost:3100 | http://localhost:3200 / 4318 |
| PROD | not published — SSH + in-network curl | same | same |

### LOCAL (bare Gradle services)

```bash
docker compose -f Backend/monitoring/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=DEV LOKI_URL=http://localhost:3100/loki/api/v1/push \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces ./gradlew :Audit:bootRun
```

This variant scrapes the services on host ports; the full compose stack instead discovers them
via **Docker DNS** (`dns_sd_configs`) so every replica is scraped, not just one published port.

### DEV (full compose stack)

Included and pre-wired. Direct endpoints:

- **Prometheus** (http://localhost:9090): Status → Targets shows `auth`/`audit` UP; try
  `rate(http_server_requests_seconds_count[5m])`.
- **Loki** via API: `curl -G http://localhost:3100/loki/api/v1/query --data-urlencode
  'query={app="audit-service"} |= "AUDIT"'`.
- **Tempo** (http://localhost:3200): the query API Grafana uses; traces arrive on
  `http://tempo:4318` (OTLP HTTP).

### PROD

All three publish **no host ports** (Grafana at `/grafana` is the public window). Direct access
is SSH plus a curl *inside* the compose network — note a plain `ssh -L` tunnel can't help, since
there's no host port to forward to:

```bash
gcloud compute ssh ask-app-vm --zone=us-east1-b
docker exec askapp-grafana curl -s http://prometheus:9090/api/v1/query?query=up
docker exec askapp-grafana curl -sG http://loki:3100/loki/api/v1/query \
  --data-urlencode 'query={app="auth-service"}' | head -c 500
```

## Good to know

- Every log line carries `traceId=`/`spanId=`, so Tempo ↔ Loki jumps are pre-provisioned in
  Grafana.
- Traces cross the async Kafka hop: one login = one trace spanning both services and the broker.
- Sampling is 100% (`TRACING_SAMPLING_PROBABILITY=1.0`) — fine at this volume, lower it for real
  load.
- Worked, verified queries: [Backend/docs/observability.md](../../Backend/docs/observability.md).
