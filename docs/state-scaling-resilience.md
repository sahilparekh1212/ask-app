# ⚖️ State, scaling, and resilience

_Part of the [ask-app](../README.md) documentation._

| Component | State model |
|---|---|
| UI/nginx, Auth, Audit | Stateless compute: services can be replicated |
| Redis | Shared single-use refresh-token state |
| Kafka/Redpanda | Durable audit-event log and dead-letter flow |
| PostgreSQL + pgvector | Durable audit records and vector index |
| Prometheus, Loki, Tempo | Persistent telemetry stores |
| Grafana | Config-as-code dashboards and datasources; anonymous read-only viewing |
| RAG index | Stateful but reconstructible from the corpus bundled into the Audit image |

The `common` module's newest-wins limiter is a deliberate exception: it tracks
the active Java worker thread for each `userId + method + path` key, so it must
stay local to each service instance. It is request deduplication, not a shared
rate counter; a superseded request is interrupted and returns `429` with
`Retry-After`. Audit mutations use a transactional checkpoint so an interrupted,
superseded request rolls back rather than leaving partial writes.
