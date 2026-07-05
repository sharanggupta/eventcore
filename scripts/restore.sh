#!/usr/bin/env bash
# Restore an export bundle into a FRESH stack. Destroys current local data.
# Usage: ./scripts/restore.sh <bundle.sql.gz> --into-fresh-stack
set -euo pipefail

BUNDLE="${1:?usage: restore.sh <bundle.sql.gz> --into-fresh-stack}"
[ "${2:-}" = "--into-fresh-stack" ] || {
  echo "This DESTROYS the current stack's data and restores $BUNDLE."
  echo "Re-run with:  ./scripts/restore.sh $BUNDLE --into-fresh-stack"
  exit 1
}

DB_USER="${DB_USER:-eventcore}"
DB_NAME="${DB_NAME:-eventcore}"

echo "recreating a fresh database volume..."
docker compose down -v >/dev/null
docker compose up -d db >/dev/null
until docker compose exec -T db pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; do sleep 1; done
sleep 2  # let the init scripts finish

echo "restoring (TimescaleDB pre/post restore around the load)..."
docker compose exec -T db psql -q -U "$DB_USER" -d "$DB_NAME" \
  -c "CREATE EXTENSION IF NOT EXISTS timescaledb; SELECT timescaledb_pre_restore();"
gunzip -c "$BUNDLE" | docker compose exec -T db psql -q -U "$DB_USER" -d "$DB_NAME"
docker compose exec -T db psql -q -U "$DB_USER" -d "$DB_NAME" \
  -c "SELECT timescaledb_post_restore();"

docker compose up -d >/dev/null
echo "restored from $BUNDLE - stack is up"
