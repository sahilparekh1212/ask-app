# How To — Grafana

_Set up, access, and use Grafana at every level of the app — LOCAL, DEV, and PROD._

**Jump to:** [LOCAL](#local-bare-gradle-services) · [DEV](#dev-full-compose-stack) ·
[PROD](#prod) · [Using it](#using-it-any-level)

Grafana is the system view: dashboards and Explore over Prometheus (metrics), Loki (logs), Tempo
(traces), and optionally Sentry. Datasources and the *ask-app Overview* dashboard are provisioned
as code (`Backend/monitoring/grafana/`), so every level looks the same once it's up.

## At each level

| Level | URL | Access |
|---|---|---|
| LOCAL (bare Gradle) | http://localhost:3000 | `admin` / `admin` |
| DEV (compose stack) | http://localhost:4200/grafana (also :3000) | anonymous read-only; `admin`/`admin` to edit |
| PROD | https://ask-app.sahilparekh1212.com/grafana | anonymous **Viewer** (public); admin password = `GRAFANA_ADMIN_PASSWORD` secret |

### LOCAL (bare Gradle services)

Telemetry is **off under the LOCAL profile** (console-only), so run the monitoring stack and
start the services under `DEV` pointing at it:

```bash
docker compose -f Backend/monitoring/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=DEV LOKI_URL=http://localhost:3100/loki/api/v1/push \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces ./gradlew :Audit:bootRun
```

Grafana at `http://localhost:3000` (admin/admin) → *ask-app Overview*.

### DEV (full compose stack)

Nothing to set up — `docker compose up` includes Grafana, pre-wired. The UI's nginx proxies
`/grafana` the same way prod's Caddy does, so `http://localhost:4200/grafana` mirrors the prod
posture (anonymous read-only), while `admin`/`admin` still lets you edit dashboards locally.

### PROD

Published read-only at **`/grafana`** on the main domain (Caddy route; Grafana owns the prefix
via `GF_SERVER_SERVE_FROM_SUB_PATH`). Anonymous visitors get the Viewer role — dashboards and
Explore work, nothing is editable. To sign in as admin, use the `GRAFANA_ADMIN_PASSWORD` repo
secret; the deploy **resets the admin password on every run** because
`GF_SECURITY_ADMIN_PASSWORD` only applies when Grafana initializes a fresh database (silent
drift otherwise).

## Using it (any level)

- **Dashboards → ask-app Overview**: request rate, per-endpoint p95/p99, JVM heap, 5xx panel,
  live logs.
- **Explore → Prometheus**: e.g. `up == 1` (which instances are scraped),
  `rate(http_server_requests_seconds_count[5m])`.
- **Explore → Loki**: `{app="audit-service"} |= "AUDIT"` — the structured audit lines.
- **Explore → Tempo**: search or TraceQL; a demo login is one trace across the Kafka hop
  (`http post /auth/login` → `audit.events send` → `audit.events receive`). Log lines carry
  `traceId=`, and the Tempo↔Loki correlation is pre-provisioned.
- **Sentry datasource** (optional): lights up when `SENTRY_ORG_SLUG`/`SENTRY_AUTH_TOKEN` are set.

Worked examples, all verified against the running stack:
[Backend/docs/observability.md](../../Backend/docs/observability.md).
