# 📊 Observability and external reporting

_Part of the [ask-app](../README.md) documentation._

- Prometheus polls the Spring services' metrics endpoints.
- Services send structured logs to Loki and OpenTelemetry traces to Tempo.
- Grafana reads all three sources and is served at `/grafana` in production.
- The Angular SPA reports route-level page views to GA4 only when a measurement
  ID is configured.
- UI, Auth, and Audit send errors to Sentry only when their DSNs are configured;
  performance tracing remains with Tempo.
- The assistant records each chat / MCP-search interaction to an `ai_trace` table
  (query, pre/post-rerank rankings, model, reply, latencies, tokens) plus Micrometer
  metrics, for measuring and improving answer accuracy — see
  [Backend observability guide](../Backend/docs/observability.md#ai-answer-quality-traces-ai_trace)
  and [ADR-0014](../Backend/docs/adr/0014-ai-interaction-tracing.md).
