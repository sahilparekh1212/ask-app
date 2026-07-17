# ADR-0011: Apache Kafka broker over Redpanda in the compose stack

**Status:** Accepted (2026-07-16; owner-requested swap)

## Context

The event-driven audit pipeline (ADR-0006) originally ran on **Redpanda** — a Kafka-API-compatible
broker reimplemented in C++ — in every compose environment (local, e2e CI, the prod VM). Redpanda
was picked purely for its operational footprint: one small binary, no ZooKeeper, no JVM, a fast
dev-container mode. The application never depended on Redpanda itself: Auth and Audit use standard
`spring-kafka` clients (`KafkaTemplate`, `@KafkaListener`) over the Kafka wire protocol, and no
Redpanda-specific API or tooling was used beyond its bundled console.

That created a describability gap: the stack was *documented and discussed* as "Kafka", but the
process actually running was a third-party emulation of it. For a portfolio project whose value is
"what's claimed is what's deployed", the owner chose authenticity over footprint.

## Decision

Run the real **Apache Kafka** broker (`apache/kafka` image) in **single-node KRaft mode** — the
broker is its own controller, so there is still no ZooKeeper sidecar and the stack stays at one
broker container, same as before. The Redpanda console is replaced by **Kafbat Kafka UI**
(the maintained fork of provectus/kafka-ui) locally; in production the UI joins Adminer and
Redis Insight behind the never-activated `local-tools` profile (its predecessor ran port-withdrawn
and unreachable — idle weight with no consumer).

The swap is confined to compose files and docs. The broker keeps the same listener shape
(in-network `kafka:9092`, host `localhost:19092`), the same auto-created `audit.events` /
`audit.events.DLT` topics, and the same health-gated `depends_on` contract; the services only see
a different `KAFKA_BOOTSTRAP_SERVERS` host name. No Java or Angular code changed — which is itself
the strongest evidence the app was Kafka-native all along.

## Alternatives considered

- **Keep Redpanda.** Functionally fine — nothing was broken. Rejected on the authenticity
  argument above: "we run Kafka" should mean Kafka, and interviews are easier when the named
  technology is the running one.
- **Confluent images (`confluentinc/cp-kafka`).** The historical default, but carries the
  Confluent platform's licensing/branding baggage; the ASF's own `apache/kafka` image is the
  upstream artifact and supports env-var configuration directly.
- **Bitnami Kafka.** Well-maintained, but a repackaging — same argument as above: if the point
  of the swap is running the real thing, use the upstream image.

## Consequences

- **Heavier broker process.** Kafka is a JVM; Redpanda was a single C++ binary. Mitigated with
  `KAFKA_HEAP_OPTS=-Xmx512m` (the workload is one topic + DLT), sized for the prod e2-standard-2
  VM. The healthcheck also costs more — the image ships no curl/nc, so the probe spawns a JVM CLI
  (`kafka-broker-api-versions.sh`) per check; interval and `start_period` are set accordingly.
- **Complete-config requirement.** Setting any `KAFKA_*` env var makes the `apache/kafka` image
  ignore its bundled `server.properties`, so the compose service must carry the full KRaft config
  (node id, roles, quorum voters, listener map, single-replica internal topics). Documented inline
  in both compose files.
- **Ephemeral log dirs, as before.** Neither broker was given a volume: on container recreation
  the log (and a KRaft cluster id) is regenerated. Acceptable because the audit trail's durable
  home is Postgres, ADR-0006 already accepts at-most-once delivery, and consumers dedup by
  `eventId`.
- Production still wants a managed/multi-broker cluster with replication ≥3 (unchanged from
  `docs/deployment.md`); this ADR only changes what the single-broker dev/VM container runs.
