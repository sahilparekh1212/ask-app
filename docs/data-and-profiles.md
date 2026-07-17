# 🗄️ Data, schema & profiles

_Part of the [ask-app](../README.md) documentation._

The backing stores and their state models are covered in
[State, scaling, and resilience](state-scaling-resilience.md); two design points
that aren't obvious from it:

- **Liquibase owns the schema** — a single dialect-neutral changelog runs unmodified
  on both H2 and Postgres (`ddl-auto=none`), so the schema is a versioned, reviewable
  artifact and the H2/Postgres split can't drift. pgvector is the vector store in
  Postgres environments; an in-memory store stands in on H2.
- **The database is chosen by Spring profile, not by where the service runs** — only
  `LOCAL` hardcodes H2; every other profile takes its datasource from the environment
  (12-factor), so the same image runs on Postgres wherever it's wired up:

| Profile | Database | Used by |
|---|---|---|
| `LOCAL` | H2 in-memory (hardcoded) | bare `gradlew bootRun`; the test suite |
| `DEV` | Postgres (from env, H2 fallback) | the local Docker Compose stack; a shared dev server |
| `SIT` / `UAT` / `PROD` | Postgres (from env) | pre-prod and production |

So `docker compose up` runs the services under `DEV` against the real pgvector Postgres
container — not `LOCAL`/H2 (which would ignore the container). Tests stay on H2 by design
(fast, offline — see the ADRs); the E2E job and prod exercise Postgres.

## 🔑 Indexes

Every index is declared in two places that must agree: the JPA `@Index` on the entity (so
Hibernate's `validate` on PROD/UAT passes) and the Liquibase changeset (which actually creates
it under `ddl-auto=none`). Each one backs a specific query the API runs.

| Index | Table | Columns | Backs |
|---|---|---|---|
| `idx_audit_active_created` | `audit_logs` | `deleted, created_at` | Default listing (active rows, newest first) and `createdAt` range filters; the leading `deleted` lets the optimizer skip soft-deleted rows. |
| `idx_audit_entity_type_action` | `audit_logs` | `entity_type, action` | Equality filters and the grouped `/stats` aggregation. |
| `idx_audit_event_id` (unique) | `audit_logs` | `event_id` | Idempotent Kafka consumption — at most one row per source event id. |
| `idx_audit_created_at` | `audit_logs` | `created_at` | The retention purge's `DELETE WHERE created_at < ?` (spans active **and** soft-deleted rows, so the `deleted`-led index above can't serve it) and time-range reads that don't constrain `deleted`. |
| `idx_secmaster_instrument` (unique) | `security_master` | `instrument_id` | The unique business key + point lookups (`GET /securities/{instrumentId}`). |
| `idx_secmaster_asset_class` | `security_master` | `asset_class` | Equality filter on the reference-data listing. |
| `idx_secmaster_currency` | `security_master` | `currency` | Equality filter on the reference-data listing. |

The reference-data listing's `assetClass`/`currency` filters are low-cardinality equality
predicates already covered by the two single-column indexes; a composite was not added because
the two filters are independent (either can be applied alone) and the dataset is small enough
that combining them buys nothing measurable.

## 👓 Reporting view — `v_audit_daily_summary`

There **is** scope for a view: the audit trail is naturally reported on by day, and a view lets
BI tools, Grafana, or ad-hoc SQL read a pre-shaped rollup without re-deriving the grouping each
time. `v_audit_daily_summary` rolls active audit rows up per calendar day / entity type / action:

```sql
SELECT CAST(created_at AS DATE) AS event_date,
       entity_type,
       action,
       COUNT(*) AS event_count
FROM audit_logs
WHERE deleted = false
GROUP BY CAST(created_at AS DATE), entity_type, action;
```

The app's own `/stats` endpoint still aggregates live via Criteria, so nothing in the request
path depends on the view — it is a read-only convenience. Steps to create it (already applied):

1. **Add a Liquibase changeset** (`db.changelog-master.yaml`) using the `createView` change with
   `replaceIfExists: true` and the `selectQuery` above. `CAST(... AS DATE)` is standard SQL, so
   the same definition runs on both H2 and Postgres — no `dbms`-specific changeset needed.
2. **Give it a `rollback`** with `dropView` so a Liquibase rollback removes it cleanly.
3. **Leave it unversioned in JPA.** Hibernate runs `ddl-auto=none`/`validate` and does not map or
   validate views, so there is nothing to add on the entity side; Liquibase is the sole owner.
4. **Query it** like any table: `SELECT * FROM v_audit_daily_summary WHERE event_date >= ?`.

To wire it into the application later, map it with an `@Entity @Immutable @Table(name =
"v_audit_daily_summary")` read-only entity (composite key on the three grouping columns) or query
it through a native query — but that step is intentionally deferred until a caller needs it.

## 🗂️ Reference data (security master)

`security_master` is a batch-loaded, read-heavy dataset styled as financial reference data
(synthetic, non-PII). On first startup `RefDataSeeder` bulk-loads 1000 rows via the Spring Batch
ingestion job (idempotent — it skips if the table already has rows), and a scheduled daily batch
appends a small number of new rows (default 50), each run starting at the current row count so the
generated instrument range is entirely new. The rows are also chunked and indexed into RAG, so the
assistant and MCP search can answer questions about the dataset. Sizes and cadence are configurable
(`refdata.seed.count`, `refdata.ingest.schedule.count`, `refdata.ingest.schedule.cron`).

## 🧹 Retention

The audit trail grows with every login, token refresh, chat, RAG, and MCP call, so it is bounded
by a retention purge: a scheduled daily job (`AuditLogPurgeScheduler`) **hard-deletes** audit rows
older than **3 months** (90 days, configurable via `audit.purge.retention-days`). This is a
permanent delete — distinct from the user-initiated soft-delete flag — because the goal is to
reclaim storage, and it rides `idx_audit_created_at`. Reference data is append-only master data
and is deliberately **not** purged.
