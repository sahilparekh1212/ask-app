# How To — Postgres (and pgvector)

_Set up, connect to, and inspect the database (H2 console, Adminer, psql, backups) at every
level of the app — LOCAL, DEV, and PROD._

**Jump to:** [LOCAL](#local-bare-gradle-services) · [DEV](#dev-full-compose-stack) ·
[PROD](#prod) · [Good to know](#good-to-know)

Postgres is Audit's database of record: audit rows, the security-master reference data, Spring
Batch metadata, **and** the RAG vector index (`rag_chunk`, pgvector `vector(1024)`). The schema
is Liquibase-owned (`ddl-auto=none`) and dialect-neutral, so the same changelog runs on H2 and
Postgres ([ADR-0004](../../Backend/docs/adr/0004-liquibase-schema-ownership.md)).

## At each level

| Level | Database | GUI |
|---|---|---|
| LOCAL (bare Gradle) | **H2 in-memory** (no Postgres at all) | H2 console: http://localhost:8083/h2-console |
| DEV (compose stack) | `pgvector/pgvector:pg16`, `localhost:5432` | Adminer: http://localhost:8082 |
| PROD | same image on the VM; no published port | none (Adminer isn't deployed) |

### LOCAL (bare Gradle services)

The LOCAL profile hardcodes H2 — a running Postgres is ignored. H2 console: JDBC
`jdbc:h2:mem:auditdb`, user `sa`, blank password (data resets each restart). The vector store
falls back to an exact in-memory implementation (H2 can't host the pgvector extension).

### DEV (full compose stack)

Credentials are dev-only and deliberately public: db `auditdb`, user/password `audit`/`audit`.

- **Adminer** (http://localhost:8082): System *PostgreSQL*, server `postgres` (pre-filled) →
  browse `audit_logs`, `security_master`, `rag_chunk`, the Liquibase + Spring Batch tables, and
  the `v_audit_daily_summary` view.
- **psql**:

```bash
psql -h localhost -p 5432 -U audit -d auditdb              # from the host
docker compose exec postgres psql -U audit -d auditdb      # no local psql needed
# taste of pgvector:
#   SELECT source, count(*) FROM rag_chunk GROUP BY source ORDER BY 2 DESC LIMIT 10;
```

**Point a bare service at it instead of H2** (what compose itself does):

```bash
SPRING_PROFILES_ACTIVE=DEV SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditdb \
  SPRING_DATASOURCE_USERNAME=audit SPRING_DATASOURCE_PASSWORD=audit ./gradlew :Audit:bootRun
```

### PROD

Password comes from the `DB_PASSWORD` repo secret (`docker-compose.prod.yml`); the port is not
published. Access over SSH:

```bash
gcloud compute ssh ask-app-vm --zone=us-east1-b
docker exec -it askapp-postgres psql -U audit -d auditdb
```

**Backups**: [`deploy/backup.sh`](../../Backend/deploy/backup.sh) streams a consistent
`pg_dump | gzip | gsutil cp -` to GCS — deliberately **excluding `rag_chunk`** (the index
self-heals from the corpus bundled in the Audit image). Retention is a 30-day GCS lifecycle
rule; install + restore commands in [deployment.md §5.1](../../Backend/docs/deployment.md).

## Good to know

- Migrations run on Audit startup; a fresh database is fully migrated automatically.
- Audit rows older than 90 days are hard-deleted by a daily purge job; reference data is
  append-only and never purged.
- pgvector arrived as an image swap (`postgres:16-alpine` → `pgvector/pgvector:pg16`) — drop-in
  on the same volume, zero added ops surface ([ADR-0010](../../Backend/docs/adr/0010-rag-mcp-server.md)).
