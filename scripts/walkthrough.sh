#!/usr/bin/env bash
# End-to-end walkthrough against a running EventCore (docker compose up -d).
# Exercises: health, key issuance, auth, ingest, query, type + payload filters, metrics,
# signed webhook delivery, delivery inspection, key revocation.
# Exits non-zero on the first failed check.
set -euo pipefail

BASE="${EVENTCORE_URL:-http://localhost:8080}"
ADMIN="${ADMIN_TOKEN:-local-admin-token}"
LISTENER_PORT="${LISTENER_PORT:-9000}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

pass() { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is required for this walkthrough"; }

need curl; need jq

echo "1. Liveness"
[ "$(curl -s "$BASE/health")" = "OK" ] && pass "GET /health -> OK" \
  || fail "GET /health (is the stack up? docker compose up -d)"

echo "2. Issue an API key (admin token from .env)"
KEY=$(curl -s -X POST "$BASE/v1/api-keys" -H "X-Admin-Token: $ADMIN" \
  -H 'Content-Type: application/json' -d '{"name": "walkthrough"}' | jq -r .key)
case "$KEY" in ek_*) pass "key issued (shown once): ${KEY:0:9}..." ;; *) fail "key issuance — check ADMIN_TOKEN" ;; esac

echo "3. Authentication is enforced"
[ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/v1/events")" = "401" ] \
  && pass "request without key -> 401" || fail "expected 401 without a key"

echo "4. Ingest an event"
TYPE="walkthrough.$(date +%s)"
EVENT_ID=$(curl -s -X POST "$BASE/v1/events" -H "X-API-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"type\": \"$TYPE\", \"payload\": {\"hello\": \"world\"}}" | jq -r .id)
[ -n "$EVENT_ID" ] && [ "$EVENT_ID" != "null" ] && pass "stored event $EVENT_ID" || fail "ingest"

echo "5. Query with a type filter"
COUNT=$(curl -s -H "X-API-Key: $KEY" "$BASE/v1/events?type=$TYPE" | jq '.items | length')
[ "$COUNT" = "1" ] && pass "?type=$TYPE returns exactly that event" || fail "type filter (got $COUNT items)"

echo "6. Query with a payload field filter"
COUNT=$(curl -s -H "X-API-Key: $KEY" "$BASE/v1/events?type=$TYPE&payload.hello=world" | jq '.items | length')
[ "$COUNT" = "1" ] && pass "?payload.hello=world matches the event" || fail "payload filter (got $COUNT items)"
MISS=$(curl -s -H "X-API-Key: $KEY" "$BASE/v1/events?type=$TYPE&payload.hello=nope" | jq '.items | length')
[ "$MISS" = "0" ] && pass "payload filter compares exact values" || fail "payload filter matched a wrong value"

echo "7. Metrics are scrapeable without a key"
curl -s "$BASE/metrics" | grep -q 'eventcore_events_ingested_total' \
  && pass "/metrics serves Prometheus text" || fail "/metrics"

echo "8. Signed webhook delivery"
if command -v python3 >/dev/null 2>&1 && command -v openssl >/dev/null 2>&1; then
  LISTENER_OUT=$(mktemp)
  python3 "$SCRIPT_DIR/webhook-listener.py" "$LISTENER_PORT" > "$LISTENER_OUT" 2>&1 &
  LISTENER_PID=$!
  trap 'kill $LISTENER_PID 2>/dev/null || true' EXIT
  sleep 1

  WEBHOOK=$(curl -s -X POST "$BASE/v1/webhooks" -H "X-API-Key: $KEY" \
    -H 'Content-Type: application/json' \
    -d "{\"url\": \"http://host.docker.internal:$LISTENER_PORT/hooks\", \"eventTypes\": [\"$TYPE\"]}")
  SECRET=$(echo "$WEBHOOK" | jq -r .secret)
  WEBHOOK_ID=$(echo "$WEBHOOK" | jq -r .id)
  case "$SECRET" in whsec_*) pass "webhook registered with signing secret" ;; *) fail "webhook registration" ;; esac

  curl -s -o /dev/null -X POST "$BASE/v1/events" -H "X-API-Key: $KEY" \
    -H 'Content-Type: application/json' -d "{\"type\": \"$TYPE\", \"payload\": {\"n\": 2}}"
  sleep 3

  BODY=$(grep '^body:' "$LISTENER_OUT" | head -1 | cut -d' ' -f2-)
  RECEIVED_SIG=$(grep '^signature:' "$LISTENER_OUT" | head -1 | awk '{print $2}')
  [ -n "$BODY" ] && pass "delivery received by local consumer" || fail "no delivery arrived (Linux: check host.docker.internal mapping)"

  EXPECTED_SIG="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $NF}')"
  [ "$RECEIVED_SIG" = "$EXPECTED_SIG" ] && pass "HMAC signature verifies" || fail "signature mismatch"

  DELIVERED=$(curl -s -H "X-API-Key: $KEY" "$BASE/v1/deliveries?status=delivered" | jq '.items | length')
  [ "$DELIVERED" -ge 1 ] && pass "delivery visible via GET /v1/deliveries" || fail "delivery not recorded"

  curl -s -o /dev/null -X DELETE "$BASE/v1/webhooks/$WEBHOOK_ID" -H "X-API-Key: $KEY"
  pass "webhook removed"
else
  echo "  SKIP webhook delivery (needs python3 + openssl)"
fi

echo "9. Revoke the key"
KEY_ID=$(curl -s -X POST "$BASE/v1/api-keys" -H "X-Admin-Token: $ADMIN" \
  -H 'Content-Type: application/json' -d '{"name": "walkthrough-doomed"}' | jq -r .id)
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/v1/api-keys/$KEY_ID" -H "X-Admin-Token: $ADMIN")
[ "$STATUS" = "204" ] && pass "revocation -> 204" || fail "revocation"

printf '\n\033[32mAll checks passed.\033[0m\n'
