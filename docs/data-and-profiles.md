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
