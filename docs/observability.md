# 📊 Observability and external reporting

_Part of the [ask-app](../README.md) documentation._

- Prometheus polls the Spring services' metrics endpoints.
- Services send structured logs to Loki and OpenTelemetry traces to Tempo.
- Grafana reads all three sources and is served at `/grafana` in production.
- The Angular SPA reports route-level page views to GA4 only when a measurement
  ID is configured.
- UI, Auth, and Audit send errors to Sentry only when their DSNs are configured;
  performance tracing remains with Tempo.
