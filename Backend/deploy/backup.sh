#!/usr/bin/env bash
#
# Nightly logical backup of the audit database to a GCS bucket.
#
# The audit_logs rows are the only data that actually needs a backup: the pgvector `rag_chunk`
# index self-heals on restart via the content-hash indexer, so it's excluded to keep dumps
# small and the restore trivial (nothing to re-embed). `pg_dump` is a consistent snapshot, so
# it's safe to run against the live container with no downtime.
#
# Install on the VM (one cron line — the honest "backups exist" answer):
#   BACKUP_BUCKET=gs://ai-sandbox-backups   # created once, see docs/deployment.md §5.1
#   ( crontab -l 2>/dev/null; echo "15 3 * * * BACKUP_BUCKET=$BACKUP_BUCKET /opt/ai-sandbox/deploy/backup.sh >> /var/log/ai-sandbox-backup.log 2>&1" ) | crontab -
#
# Retention is handled by a bucket lifecycle rule (delete after 30 days), not by this script —
# see docs/deployment.md §5.1.
set -euo pipefail

BUCKET="${BACKUP_BUCKET:?set BACKUP_BUCKET, e.g. gs://ai-sandbox-backups}"
CONTAINER="${PG_CONTAINER:-aisandbox-postgres}"
DB="${POSTGRES_DB:-auditdb}"
DB_USER="${POSTGRES_USER:-audit}"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
object="${BUCKET%/}/auditdb-${stamp}.sql.gz"

# Stream the dump straight to GCS: pg_dump -> gzip -> gsutil, no temp file on the VM disk.
docker exec "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB" --no-owner --exclude-table='rag_chunk' \
  | gzip -9 \
  | gsutil -q cp - "$object"

echo "$(date -u +%FT%TZ) backed up ${DB} (excl. rag_chunk) -> ${object}"
