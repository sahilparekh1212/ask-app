# Using the observability stack (Grafana, Prometheus, Loki, Tempo)

This is the **system view** of the deployment — how the servers are performing. (The app's own
audit dashboard is the **domain view** — what users and agents did. Two questions, two views;
the About page in the UI explains the split.)

## Where to find it

| Environment | Grafana | Access |
|---|---|---|
| Local compose stack | http://localhost:4200/grafana (old http://localhost:3000 redirects here) | Anonymous read-only; `admin` / `admin` to edit dashboards |
| Production | https://ask-app.sahilparekh1212.com/grafana | Anonymous read-only; admin password is the `GRAFANA_ADMIN_PASSWORD` repo secret |

Grafana is the only published window. Prometheus, Loki and Tempo are never exposed directly in
prod (locally they happen to have host ports — `:9090`, `:3100`, `:3200` — for direct poking);
their data is reachable through Grafana's **Explore** (compass icon → datasource dropdown), and
anonymous visitors hold the Viewer role: dashboards and Explore work, nothing is editable.

**Start here:** Dashboards → ask-app → **ask-app Overview** — request/error rates, p95/p99
latency, JVM heap, and a live logs panel on one screen.

## Prometheus (metrics) — Explore → *Prometheus*

Both services expose Micrometer metrics at `/actuator/prometheus`; Prometheus discovers every
replica via Docker DNS (`dns_sd_configs`), so a scaled service is scraped per-pod. All series
are tagged `application` (service) and `podName` (replica).

```promql
# Are both services up? (expect one series per replica, value 1)
up

# Request throughput per service (req/s)
sum by (application) (rate(http_server_requests_seconds_count[1m]))

# p95 latency per endpoint (switch 0.95 → 0.99 for p99)
histogram_quantile(0.95, sum by (le, application, uri) (rate(http_server_requests_seconds_bucket[5m])))

# 5xx error rate per service
sum by (application) (rate(http_server_requests_seconds_count{status=~"5.."}[1m]))

# Rate-limiter sheds: 429s on the audit search endpoint
sum (rate(http_server_requests_seconds_count{status="429", uri="/api/v1/audit-logs/search"}[5m]))

# JVM heap in use per service
sum by (application) (jvm_memory_used_bytes{area="heap"})

# Kafka consumer throughput (audit events persisted from the topic)
rate(spring_kafka_listener_seconds_count[5m])
```

## Loki (logs) — Explore → *Loki*

Each service ships its logs via a logback Loki appender (active in DEV/SIT/UAT/PROD; the LOCAL
profile stays console-only). The only stream label is `app`; everything else in a line —
`traceId`, `spanId`, `requestId`, `userId`, `pod`, `url` — is searched with line filters
(`|=` contains, `|~` regex, `!=` excludes).

```logql
# Everything from one service
{app="audit-service"}

# Both services at once
{app=~".+-service"}

# Errors only
{app=~".+-service"} |~ "ERROR|WARN"

# Follow one user's requests (MDC userId is stamped on every line)
{app="audit-service"} |= "userId=demo-user"

# Follow one request across log lines (requestId from the MDC correlation filter)
{app="auth-service"} |= "requestId=<paste-id>"

# The structured AUDIT access-log lines, with their duration measurements
{app="audit-service"} |= "AUDIT" |= "durationMs"

# All log lines belonging to one trace (paste a traceId from Tempo)
{app=~".+-service"} |= "<traceId>"
```

## Tempo (traces) — Explore → *Tempo*

Traces are exported via OTLP (Micrometer Tracing → OpenTelemetry). The one to look at: **a login
crossing the Kafka hop** — sign in on the app, then search recent traces for service
`auth-service`. The trace shows the HTTP server span, the `audit.events send` producer span, and
the `audit.events receive` consumer span in **one** trace, because the `@Async` fire-and-forget
publish propagates the trace context (a real bug once — see the backend README's observability
war stories).

Trace → logs is wired: the log icon on any span jumps to the matching Loki lines, since every
log line carries `traceId=` in plain text (the datasource does a substring search — no custom
label mapping needed).

## Try it end-to-end (2 minutes)

1. Sign in at the app (demo / demo) and click around the dashboard and chat.
2. Open Grafana → **ask-app Overview**: the request-rate panel ticks up.
3. Explore → Loki → `{app="auth-service"} |= "LOGIN"`: your login's log line, with its
   `traceId` and `userId`.
4. Copy that `traceId` → Explore → Tempo → paste: the whole login → Kafka → persist flow as one
   trace.
5. Back on the app's Dashboard tab: the same login is a `User / LOGIN` row — the domain view of
   the event you just traced through the system view.

## Error tracking + analytics (Sentry across the stack, GA4 in the SPA)

All of these are config-driven and **fully dormant when their IDs are empty** (dev is always
empty — local clicks never pollute the real analytics, local errors never page anyone):

- **Google Analytics 4** (SPA) — a hand-rolled gtag loader
  (`core/analytics/analytics.service.ts`) reports a `page_view` per router navigation
  (`send_page_view` disabled — a SPA has exactly one full page load). Only route paths are
  reported; the app puts no PII in URLs. View at https://analytics.google.com (GA's own UI —
  deliberately not bound into Grafana; the only Grafana paths are unmaintained community
  plugins).
- **Sentry, frontend** — `@sentry/angular` error monitoring (no performance/replay
  integrations), initialized before bootstrap in `main.ts`; Sentry's `ErrorHandler` replaces
  Angular's default only when a DSN is configured (`environment.ts`).
- **Sentry, backend** — `sentry-spring-boot-starter-jakarta` in both services captures
  unhandled exceptions. One Sentry project per service; the DSN arrives as `SENTRY_DSN`
  (compose maps `SENTRY_DSN_AUTH`/`SENTRY_DSN_AUDIT` per container), tagged with the active
  Spring profile as the Sentry environment. Performance tracing stays off — Tempo owns
  tracing; `send-default-pii=false`.
  **Gotcha handled:** the SDK's `SentryExceptionResolver` runs at `LOWEST_PRECEDENCE`, but
  each service has a `@RestControllerAdvice` catch-all (`handleAll`) that resolves the
  exception first and stops DispatcherServlet's resolver chain — so the SDK would never see a
  single controller 500. The catch-all therefore calls `Sentry.captureException(ex)`
  explicitly, positioned after the rate-limit discard check so only genuine 500s report
  (400/403/404/503 have their own handlers; a 429 shed is expected load-shedding, not an
  error). A unit test asserts both the capture and the no-capture-on-429.

**Sentry in Grafana:** the official `grafana-sentry-datasource` plugin is installed
(`GF_INSTALL_PLUGINS`) and a `Sentry` datasource is provisioned with `SENTRY_ORG_SLUG` +
`SENTRY_AUTH_TOKEN` from the environment (repo variable + secret in prod; export them locally
to light it up). The token is org-scoped, so Explore → Sentry can query issues/events from
all three projects (UI, Auth, Audit) next to the Prometheus/Loki/Tempo signals.

## Operating notes

- Dashboards and datasources are **provisioned from the repo** (`monitoring/grafana/…`) and
  mounted read-only — edits made in the Grafana UI don't survive a container recreate; change
  the JSON in the repo instead.
- Config lives under `monitoring/` (`prometheus/`, `loki/`, `tempo/`, `grafana/`). The compose
  stack uses `prometheus-compose.yml` (Docker-DNS discovery); the sibling `prometheus.yml` is
  for the narrower `monitoring/docker-compose.yml` + `gradlew bootRun` loop, where Grafana is
  plain http://localhost:3000.
- Prod exposure details (Caddy route, anonymous Viewer, admin secret): `docs/deployment.md` §10.
