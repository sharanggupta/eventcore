# EventCore

Self-hosted event audit log with reliable webhook delivery.

Send your application's events to EventCore over HTTP; it stores them in
TimescaleDB, lets you query them newest-first with cursor pagination and type
filtering, and pushes each event to your registered webhooks — signed, and
retried with exponential backoff until delivered.

**Features**

- `POST /v1/events` — ingest events with arbitrary JSON payloads
- `GET /v1/events` — newest-first queries with cursor pagination and `?type=` filtering
- Webhooks — register/delete endpoints, HMAC-SHA256 signed deliveries, 5 retries with exponential backoff, outbox survives restarts
- API keys — hashed at rest, revocable, admin-token-guarded issuance

## Quick start

Prerequisites: Docker with Compose. (`jq` is used in the examples below.)

```bash
git clone git@github.com:sharanggupta/eventcore.git
cd eventcore
docker compose up --build -d
```

Wait a few seconds for the containers to become healthy, then:

```bash
curl http://localhost:8080/health
```

```
OK
```

Everything below is copy-paste-able in order. The default admin token is
`local-admin-token`, set in [.env](.env) — change it for anything beyond a
local try-out.

## Five-minute walkthrough

### 1. Issue an API key

All `/v1/*` endpoints except key issuance require an API key. Mint one with
the admin token:

```bash
curl -s -X POST http://localhost:8080/v1/api-keys \
  -H 'X-Admin-Token: local-admin-token' \
  -H 'Content-Type: application/json' \
  -d '{"name": "my-first-key"}' | tee /tmp/key.json | jq .
```

```json
{
  "id": "bc96b034-4ed0-4e6e-a8b6-f21458be19b5",
  "name": "my-first-key",
  "key": "ek_PNZfS66wEo5zHrMtugxus80eflvgPTc0XvdCow4S3Bs",
  "createdAt": "2026-07-05T07:17:54.964541671Z"
}
```

The `key` is shown **only once** — EventCore stores just its SHA-256 hash.
Keep it in a variable for the rest of the walkthrough:

```bash
KEY=$(jq -r .key /tmp/key.json)
```

Without a key, the API refuses politely:

```bash
curl -s http://localhost:8080/v1/events | jq .
```

```json
{
  "error": "a valid X-API-Key header is required"
}
```

### 2. Ingest events

`type` is required; `payload` is any JSON you like:

```bash
curl -s -X POST http://localhost:8080/v1/events \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"type": "user.signed_up", "payload": {"userId": "u_123", "plan": "pro"}}' | jq .
```

```json
{
  "id": "852d42c4-5157-44fe-b89f-8f96596a3ba6",
  "time": "2026-07-05T07:17:55.024748921Z",
  "type": "user.signed_up"
}
```

Add a second event so there is something to filter:

```bash
curl -s -X POST http://localhost:8080/v1/events \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"type": "invoice.paid", "payload": {"invoiceId": "inv_7", "amountCents": 4900}}' | jq .
```

### 3. Query events

Newest first:

```bash
curl -s -H "X-API-Key: $KEY" 'http://localhost:8080/v1/events?limit=10' | jq .
```

```json
{
  "items": [
    {
      "id": "971c6161-09b5-4c6e-8198-3a87c2696994",
      "time": "2026-07-05T07:17:55.061274Z",
      "type": "invoice.paid",
      "payload": {"invoiceId": "inv_7", "amountCents": 4900}
    },
    {
      "id": "852d42c4-5157-44fe-b89f-8f96596a3ba6",
      "time": "2026-07-05T07:17:55.024749Z",
      "type": "user.signed_up",
      "payload": {"plan": "pro", "userId": "u_123"}
    }
  ],
  "nextCursor": null
}
```

`limit` defaults to 50 (max 200). When more pages exist, `nextCursor` is a
token — pass it back as `cursor` to continue where you left off; it is `null`
on the last page.

Filter by type:

```bash
curl -s -H "X-API-Key: $KEY" 'http://localhost:8080/v1/events?type=invoice.paid' | jq .
```

```json
{
  "items": [
    {
      "id": "971c6161-09b5-4c6e-8198-3a87c2696994",
      "time": "2026-07-05T07:17:55.061274Z",
      "type": "invoice.paid",
      "payload": {"invoiceId": "inv_7", "amountCents": 4900}
    }
  ],
  "nextCursor": null
}
```

### 4. Receive webhooks

Start a tiny listener on your machine (leave it running in a second
terminal):

```bash
python3 - <<'EOF'
from http.server import BaseHTTPRequestHandler, HTTPServer

class Hook(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers["Content-Length"])).decode()
        print("signature:", self.headers.get("X-EventCore-Signature"), flush=True)
        print("body:", body, flush=True)
        self.send_response(200)
        self.end_headers()
    def log_message(self, *args):
        pass

HTTPServer(("", 9000), Hook).serve_forever()
EOF
```

Register it (`host.docker.internal` lets the container reach your machine):

```bash
curl -s -X POST http://localhost:8080/v1/webhooks \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"url": "http://host.docker.internal:9000/hooks"}' | tee /tmp/webhook.json | jq .
```

```json
{
  "id": "0697096d-57f5-43f6-bb2b-fb766ab7d773",
  "createdAt": "2026-07-05T07:17:55.110511296Z",
  "url": "http://host.docker.internal:9000/hooks",
  "secret": "whsec_ldR9MjVZEUXmy2fyShSPZajiYSr79KBFle_4IVYrer8"
}
```

Like API keys, the `secret` is shown only at registration — it never appears
in `GET /v1/webhooks`. Now post an event and watch the listener terminal:

```bash
curl -s -o /dev/null -X POST http://localhost:8080/v1/events \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"type": "order.shipped", "payload": {"orderId": "ord_42"}}'
```

Within about a second the listener prints:

```
signature: sha256=9ca9ca1e5ceabfc83fb357186310bccc8143ac293badb3d1a311ff5fe445f507
body: {"id": "fdd60af2-34c0-49a5-8962-75b4f1fd4afb", "time": "2026-07-05T07:17:55.124136379Z", "type": "order.shipped", "payload": {"orderId": "ord_42"}}
```

**Verify the signature.** It is the HMAC-SHA256 of the exact request body,
keyed with your subscription secret. Reproduce it with openssl:

```bash
SECRET=$(jq -r .secret /tmp/webhook.json)
printf '%s' '<paste the body line here>' | openssl dgst -sha256 -hmac "$SECRET"
```

The hex digest matches the value after `sha256=`. Reject any delivery whose
signature does not match — only EventCore knows the secret.

If your endpoint is down, EventCore retries up to 5 times with exponential
backoff (5s, 10s, 20s, ...). Deliveries live in a database outbox, so pending
retries survive an application restart.

### 5. Clean up

Remove the webhook (its delivery history goes with it):

```bash
curl -s -w "%{http_code}\n" -o /dev/null -X DELETE \
  "http://localhost:8080/v1/webhooks/$(jq -r .id /tmp/webhook.json)" \
  -H "X-API-Key: $KEY"
```

```
204
```

Revoke the key — it stops authenticating immediately (the record is kept,
marked revoked, for your audit trail):

```bash
curl -s -w "%{http_code}\n" -o /dev/null -X DELETE \
  "http://localhost:8080/v1/api-keys/$(jq -r .id /tmp/key.json)" \
  -H 'X-Admin-Token: local-admin-token'
```

```
204
```

```bash
curl -s -H "X-API-Key: $KEY" http://localhost:8080/v1/events | jq .
```

```json
{
  "error": "a valid X-API-Key header is required"
}
```

Stop the stack with `docker compose down` (add `-v` to also wipe the data).

## API reference

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health` | none | Liveness check, returns `OK` |
| POST | `/v1/api-keys` | `X-Admin-Token` | Issue an API key (plaintext shown once) |
| DELETE | `/v1/api-keys/{id}` | `X-Admin-Token` | Revoke a key immediately |
| POST | `/v1/events` | `X-API-Key` | Ingest an event (`type` required, `payload` optional JSON) |
| GET | `/v1/events` | `X-API-Key` | List newest-first; `limit`, `cursor`, `type` params |
| GET | `/v1/deliveries` | `X-API-Key` | List webhook deliveries newest-first; `status` (pending/delivered/failed), `limit`, `cursor` params |
| POST | `/v1/webhooks` | `X-API-Key` | Register a webhook (signing `secret` shown once) |
| GET | `/v1/webhooks` | `X-API-Key` | List subscriptions (without secrets) |
| DELETE | `/v1/webhooks/{id}` | `X-API-Key` | Remove a subscription and its delivery history |

Errors always come back as `{"error": "<what went wrong>"}` with `400`
(invalid request), `401` (missing/bad credentials), or `404` (no such thing).

## Configuration

Set in [.env](.env) (used by Docker Compose) or as environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| DB_NAME | eventcore | Database name |
| DB_USER | eventcore | Database user |
| DB_PASSWORD | eventcore | Database password |
| DB_PORT | 5432 | Database port |
| SERVER_PORT | 8080 | Application port |
| ADMIN_TOKEN | (unset) | Token for issuing/revoking API keys; those endpoints reject everything while it is unset |

Webhook delivery tuning lives in [application.yml](src/main/resources/application.yml)
under `eventcore.webhooks`: `poll-interval` (1s), `retry-backoff` (5s,
doubling per attempt), `max-attempts` (5).

## Development

```bash
./mvnw test          # integration tests via Testcontainers (Docker must be running)
./mvnw clean package # build the jar
```

CI runs `./mvnw verify` on every push and pull request.

## Stack

Java 21 · Spring Boot 4 · TimescaleDB (PostgreSQL 16) · Flyway · Docker Compose
