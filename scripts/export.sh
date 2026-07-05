#!/usr/bin/env bash
# Export the entire EventCore database to one portable bundle.
# Usage: ./scripts/export.sh [output-file.sql.gz]     (stack must be running)
set -euo pipefail

OUT="${1:-eventcore-export-$(date +%Y%m%d-%H%M%S).sql.gz}"

docker compose exec -T db pg_dump \
  -U "${DB_USER:-eventcore}" "${DB_NAME:-eventcore}" | gzip > "$OUT"

echo "exported to $OUT ($(du -h "$OUT" | cut -f1))"
echo "restore anywhere with: ./scripts/restore.sh $OUT --into-fresh-stack"
