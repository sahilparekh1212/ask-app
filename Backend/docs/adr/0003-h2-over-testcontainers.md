# ADR-0003: H2 for tests over Testcontainers

**Status:** Accepted

## Context

Audit's `@DataJpaTest`/integration tests need a real JDBC datasource to exercise JPA mappings,
the Liquibase changelog, and repository queries. PROD/UAT run Postgres
(`spring.datasource.url=${SPRING_DATASOURCE_URL}`, no default — see `application-PROD.properties`);
LOCAL/DEV/SIT default to an in-memory `jdbc:h2:mem:auditdb` so the service runs with zero external
setup. The question is what the *test suite* should run against.

## Decision

Run tests against H2 in-memory, the same way LOCAL/DEV do — not Testcontainers-managed Postgres.
Tests need to run fast and offline: in CI (no Docker-in-Docker guarantee assumed) and locally
without requiring Docker Desktop to be running (this environment doesn't always have it — see the
Trivy/Docker-image items in `TODO.md` that are explicitly "not build-verified here (no Docker)").
H2 starts in milliseconds as part of the JVM process and adds no external moving part to CI.

## Alternatives considered

- **Testcontainers + real Postgres.** The correctness-maximizing choice — catches
  Postgres-specific behavior (case sensitivity, `SERIAL` vs `IDENTITY`, real constraint/index
  semantics, JSON functions) that H2's compatibility mode can paper over or get subtly wrong.
  Rejected as the *default* test story because it makes every test run (and every contributor's
  first `./gradlew test`) depend on Docker being installed and running, which conflicts with the
  "fast, zero-setup" bar the LOCAL profile is designed around.
- **Skip integration tests entirely, unit-test the service layer only.** Rejected — the
  Liquibase changelog, the three real indexes, and `existsByEventId`-based idempotency (see the
  Kafka consumer's dedup logic) are exactly the kind of thing that looks right in code review and
  breaks at the database boundary. Those need a real JDBC round-trip to mean anything.

## Consequences

- The Liquibase changelog (`db.changelog-master.yaml`) is authored in dialect-neutral YAML
  specifically so the same migration runs unmodified against both H2 (tests/LOCAL/DEV) and
  Postgres (PROD/UAT) — this is what keeps the H2/Postgres gap from being a schema-drift risk,
  not just a query-semantics one.
- Real residual risk: a query that behaves differently under Postgres's stricter typing or
  locking semantics could pass in CI and fail in PROD. This hasn't happened yet, but the honest
  mitigation — a Testcontainers-based Postgres run as a *second*, slower CI job (not a replacement
  for the fast H2 suite) — is worth calling out as a known gap rather than an unconsidered one.
