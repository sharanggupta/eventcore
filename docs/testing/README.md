# Testing EventCore

Everything here is reproducible from a fresh clone. Each section states its
prerequisites and the exact commands.

## 1. Integration test suite

**Prerequisites**: Java 21, Docker running (Testcontainers starts a real
TimescaleDB container).

```bash
cd backend && ./mvnw test
```

Expected: `Tests run: 86, Failures: 0, Errors: 0` (count grows with features).

The suite is integration-first: every test boots the real Spring context
against a real TimescaleDB and talks HTTP through the same API clients a user
would. One shared container serves all test classes, so the whole suite runs
in about a minute. What each class covers:

| Test class | Covers |
|---|---|
| `SmokeTest` | Boot, DB connectivity, migrations, `/health`, OpenAPI docs |
| `events/EventIngestionTest` | `POST /v1/events` happy path, validation, persistence |
| `events/EventQueryTest` | Cursor pagination, type filtering, limit rules |
| `events/EventsSchemaTest` | TimescaleDB hypertable schema |
| `webhooks/WebhookSubscriptionsTest` | Register/list/delete, secrets shown once, filters, PATCH |
| `deliveries/WebhookDeliveryTest` | Signed delivery, retries, attempt history, redelivery cycles |
| `deliveries/DeliveryQueryTest` | Outbox listing/detail, bulk redelivery, error statuses |
| `security/ApiKeyManagementTest` | Issuance, SHA-256-only storage, revocation |
| `security/ApiKeyAuthenticationTest` | 401 enforcement on `/v1/**`, public paths |
| `metrics/MetricsTest` | Every exported metric |

## 2. Automated end-to-end walkthrough

**Prerequisites**: Docker Compose stack running, plus `curl`, `jq`; the
webhook step also wants `python3` and `openssl` (skipped cleanly if absent).

```bash
docker compose up --build -d
./scripts/walkthrough.sh
```

Expected output ends with `All checks passed.` The script exercises liveness,
key issuance, 401 enforcement, ingestion, filtered querying, metrics, a real
**signed** webhook delivery to a local listener (verifying the HMAC with
openssl), delivery inspection, and key revocation. It is safe to re-run.

Configuration via environment variables: `EVENTCORE_URL` (default
`http://localhost:8080`), `ADMIN_TOKEN` (default `local-admin-token`, must
match `.env`), `LISTENER_PORT` (default 9000).

## 3. Manual webhook consumer

To watch deliveries arrive while exploring the API by hand:

```bash
python3 scripts/webhook-listener.py            # terminal 1: listens on :9000
```

Register it (the app reaches your machine via `host.docker.internal`):

```bash
curl -X POST http://localhost:8080/v1/webhooks \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"url": "http://host.docker.internal:9000/hooks"}'
```

Every matching event you ingest is printed with its
`X-EventCore-Signature` header.

## 4. Failure and recovery drill

Reproduces a real outage and recovery — the scenario the delivery-ops API
exists for. With the stack up and a key in `$KEY`:

1. Register a webhook pointing at the listener **without starting it**
   (a dead consumer).
2. Ingest an event. Watch retries exhaust:
   `curl -H "X-API-Key: $KEY" 'localhost:8080/v1/deliveries?status=failed'`
   — after ~80 seconds (5 attempts, exponential backoff) the delivery is
   `failed` with `attempts: 5`.
3. Inspect why: `curl -H "X-API-Key: $KEY" localhost:8080/v1/deliveries/<id>`
   — five attempts, each with its transport error and duration.
4. Start the listener, then recover:
   `curl -X POST -H "X-API-Key: $KEY" localhost:8080/v1/deliveries/<id>/redeliver`
   — seconds later the delivery reads `delivered`, attempt 6, status 200.
5. Bulk variant: dead-letter several, then
   `curl -X POST -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{"status":"failed"}' localhost:8080/v1/deliveries/redeliver`
   → `{"requeued": N}`.

## 5. Continuous integration

Every push and pull request runs `./mvnw verify` on GitHub Actions
([.github/workflows/ci.yml](../../.github/workflows/ci.yml)) — the full
Testcontainers suite against a real database, no mocks.
