# Testing EventCore

Everything here is reproducible from a fresh clone. Each section states its
prerequisites and the exact commands. Want the guided version with the demo
app and the dashboard? Take the [full-system tour](../full-system-tour.md).

## 0. Starting and stopping the backend

Everything below (except the unit suite, which brings its own database via
Testcontainers) assumes the stack is running:

```bash
docker compose up --build -d   # build + start app (8080) and TimescaleDB
docker compose logs -f app     # watch the app logs (Ctrl-C to detach)
docker compose down            # stop, keep data
docker compose down -v         # stop and wipe the data volume
```

Confirm readiness with `curl http://localhost:8080/health` -> `OK`.

## 1. Integration test suite

**Prerequisites**: Java 21, Docker running (Testcontainers starts a real
TimescaleDB container).

```bash
cd backend && ./mvnw test
```

Expected: `BUILD SUCCESS` with `Failures: 0, Errors: 0` (~110 tests today, and
the count grows with features — match on zero failures, not the total).

The suite is integration-first: every test boots the real Spring context
against a real TimescaleDB and talks HTTP through the same API clients a user
would. One shared container serves all test classes, so the whole suite runs
in about a minute. What each class covers:

| Test class | Covers |
|---|---|
| `SmokeTest` | Boot, DB connectivity, migrations, `/health`, OpenAPI docs |
| `api/ApiErrorContractTest` | Every error path — domain and framework (malformed JSON, non-UUID path var, unknown route, wrong method) — returns the one shape `{"error": ...}` |
| `events/EventIngestionTest` | `POST /v1/events` happy path, validation, persistence |
| `events/EventQueryTest` | Cursor pagination, type filtering, limit rules, `from`/`to` time ranges, payload field search (`?payload.<field>=...`) |
| `events/EventsSchemaTest` | TimescaleDB hypertable schema |
| `webhooks/WebhookSubscriptionsTest` | Register/list/delete, secrets shown once, filters, PATCH, `payloadFields` minimization allow-list |
| `deliveries/WebhookDeliveryTest` | Signed delivery, retries, attempt history, redelivery cycles |
| `deliveries/DeliveryQueryTest` | Outbox listing/detail, `from`/`to` time ranges, bulk redelivery, error statuses |
| `pull/PullSubscriptionsTest` | Named durable cursors, peek/commit exactly-once loop, start positions, type filters, rewind, fleet lag view |
| `retention/RetentionTest` | Optional retention sweep: old-event chunk drops, delivery-history deletion, idempotence |
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

## 4. Manual pull consumer

To fetch events at your own pace instead of having them pushed to you, run
the ready-made consumer (one file, standard library only):

```bash
export EVENTCORE_API_KEY=ek_...   # issue one via POST /v1/api-keys
python3 examples/python-pull-consumer/consumer.py my-consumer beginning
```

It creates (or resumes) a durable cursor named `my-consumer` and runs the
crash-safe fetch → process → commit loop. Kill it mid-stream and restart it:
it resumes from the last committed cursor without losing an event (commits
happen after each batch, so processing must tolerate at-least-once). To
replay from an earlier point, rewind the cursor:

```bash
curl -X POST http://localhost:8080/v1/pull-subscriptions/my-consumer/rewind \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"to": "beginning"}'
```

## 5. Failure and recovery drill

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

## 6. Continuous integration

Every push to `main` and every pull request runs `./mvnw verify` on GitHub
Actions ([.github/workflows/ci.yml](../../.github/workflows/ci.yml)) — the
full Testcontainers suite against a real database, no mocks.
