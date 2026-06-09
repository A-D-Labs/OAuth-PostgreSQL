#!/bin/bash
set -e

DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${POSTGRES_DB}"
DB_USER="${POSTGRES_USER}"
BACKUP_DIR="/backups"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql.gz"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

echo "Starting backup for $DB_NAME at $(date)"

# pg_dump reads the password from PGPASSWORD.
if [ -z "$PGPASSWORD" ]; then
  echo "ERROR: PGPASSWORD is not set"; exit 1
fi

mkdir -p "$BACKUP_DIR"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"
echo "Created backup: $BACKUP_FILE"

echo "Cleaning up backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -mtime +$RETENTION_DAYS -delete
echo "Backup process completed at $(date)"
