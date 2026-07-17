# How To — Kafka

_Set up, access, and inspect Kafka (broker, UI, CLI) at every level of the app — LOCAL, DEV,
and PROD._

**Jump to:** [LOCAL](#local-bare-gradle-services) · [DEV](#dev-full-compose-stack) ·
[PROD](#prod) · [Good to know](#good-to-know)

Kafka is the event backbone: Auth (and Audit's own AI features) publish `AuditEvent`s to topic
`audit.events`; Audit consumes them idempotently, dead-lettering failures to `audit.events.DLT`.
The broker is **Apache Kafka** in single-node KRaft mode
([ADR-0011](../../Backend/docs/adr/0011-apache-kafka-over-redpanda.md)); publishes are
fire-and-forget at-most-once
([ADR-0006](../../Backend/docs/adr/0006-fire-and-forget-audit-events.md)).

## At each level

| Level | Broker | Kafka UI |
|---|---|---|
| LOCAL (bare Gradle) | optional — `Backend/kafka/docker-compose.yml`, host listener `localhost:19092` | http://localhost:8080 |
| DEV (compose stack) | included — in-network `kafka:9092`, host `localhost:19092` | http://localhost:8080 |
| PROD | included — no published ports (in-network only) | not deployed (`local-tools` profile) |

### LOCAL (bare Gradle services)

The app runs **without** a broker (publishes are fire-and-forget; events are dropped). For the
real pipeline:

```bash
docker compose -f Backend/kafka/docker-compose.yml up -d
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./gradlew :Auth:bootRun
KAFKA_BOOTSTRAP_SERVERS=localhost:19092 ./gradlew :Audit:bootRun
```

### DEV (full compose stack)

Already wired (`KAFKA_BOOTSTRAP_SERVERS=kafka:9092`, health-gated `depends_on`). Prove it: sign
in on the UI — your LOGIN event appears as an audit row within seconds.

- **Kafka UI** (http://localhost:8080): topics, messages, consumer group `audit-service` lag,
  and `audit.events.DLT` if anything dead-letters.
- **CLI** (the image bundles the standard tools):

```bash
docker exec askapp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec askapp-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic audit.events --from-beginning --max-messages 5
```

- **From the host** (your own tools): `localhost:19092`.

### PROD

Same container, zero exposure — no published ports, CLI over SSH:

```bash
gcloud compute ssh ask-app-vm --zone=us-east1-b
docker exec askapp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker logs askapp-kafka --tail 50
```

## Good to know

- Topics are **auto-created** on first use (no `NewTopic` beans); the DLT appears on the first
  dead-lettered message.
- The consumer dedups by `eventId` (unique index), so redelivery is a no-op.
- Broker data is deliberately ephemeral (no volume): the durable audit trail lives in Postgres.
- Config gotcha: the `apache/kafka` image ignores its bundled `server.properties` the moment any
  `KAFKA_*` env var is set — the compose service carries the complete KRaft config on purpose.
- A login's journey is visible as **one trace** in Tempo: see [How To — Grafana](grafana.md).
