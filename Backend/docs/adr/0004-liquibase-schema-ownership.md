# ADR-0004: Liquibase owns the schema instead of Hibernate `ddl-auto`

**Status:** Accepted

## Context

Audit is the only service with a database. Hibernate can manage the schema itself
(`ddl-auto=update`/`create`) or a dedicated migration tool can own it, with Hibernate only ever
validating (or doing nothing) at runtime. Before this decision, PROD ran
`spring.jpa.hibernate.ddl-auto=validate` against a schema nothing had ever created — there was no
migration step, so the first PROD deploy would have failed validation against an empty database.

## Decision

Add Liquibase with a baseline changelog at the default path
(`db/changelog/db.changelog-master.yaml`) that creates `audit_logs` and all three indexes
(including the unique `idx_audit_event_id` the Kafka consumer relies on for idempotency).
Hibernate now runs `ddl-auto=none` in *every* profile — Liquibase is the only thing that ever
touches DDL, in LOCAL/DEV exactly as in PROD.

## Alternatives considered

- **`ddl-auto=update` in LOCAL/DEV, `validate` in PROD (the prior state).** Convenient for
  day-to-day development — Hibernate infers DDL from the entity, no changelog to maintain — but
  it means the schema LOCAL/DEV developers actually run against is *never* the same mechanism
  that would create PROD's schema, since PROD had no creation path at all. Any entity change
  "just works" locally via `update` while silently having no corresponding PROD migration.
  Rejected because it lets schema drift between "what Hibernate inferred" and "what's actually
  versioned" go unnoticed until a real deploy.
- **Flyway instead of Liquibase.** Comparable tool, SQL-first instead of Liquibase's
  YAML/XML/JSON changesets. No strong technical reason to prefer either here; Liquibase was
  picked for its dialect-neutral changelog format, which is what lets the same changelog run
  against both H2 (tests, see ADR-0003) and Postgres (PROD/UAT) unmodified.

## Consequences

- Every schema change is now an explicit, versioned changeset — reviewable in a PR diff, and
  replayable in the same order everywhere (H2 in tests, Postgres in PROD).
- `ddl-auto=none` everywhere means a developer who adds a field to `AuditLog` and forgets to add
  a matching changeset gets a runtime failure (missing column) rather than Hibernate silently
  patching the schema for them — a deliberate tradeoff of local convenience for schema fidelity.
- Not yet exercised against a real Postgres instance in this repo (no Postgres wired into
  `docker compose` yet — tracked as the "Top-level docker-compose" TODO item); verified so far on
  H2 via `@DataJpaTest`/embedded-Kafka tests and a running-app smoke insert.
