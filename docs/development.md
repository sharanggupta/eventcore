# Developing EventCore

A tour for new contributors: where everything lives, how a request flows, and
how to add a feature the way the existing ones were added.

## The map

Everything is under [`backend/src/main/java/dev/eventcore`](../backend/src/main/java/dev/eventcore),
one package per capability. Three conventions make any file findable:

- `*Controller` — the HTTP surface (thin: validate, delegate, return a record)
- `*Store` / `*Outbox` — owns the SQL for its tables (JdbcClient, no ORM)
- records — API contracts (requests, responses); validation lives on the
  request record itself (`request.validate()`)

| Package | What lives there | Tables |
|---|---|---|
| `events` | Ingestion, querying, oldest-first readers, `EventIngestion` (the append+fan-out transaction) | `events` (hypertable) |
| `webhooks` | Subscription lifecycle: register/list/delete/filter | `webhook_subscriptions` |
| `deliveries` | Outbox, scheduled dispatcher, attempt history, redelivery | `webhook_deliveries`, `delivery_attempts` |
| `pull` | Named durable cursors: fetch/commit/rewind/fleet | `pull_subscriptions` |
| `security` | API keys + the `X-API-Key` filter | `api_keys` |
| `metrics` | `/metrics` Prometheus text | (reads the others) |
| `api` | Shared web primitives: `ApiError`, exceptions→status mapping, `Cursor`, OpenAPI config | — |
| `crypto` | `Sha256`, `HmacSha256`, `Secrets` | — |

Each package has a `package-info.java` saying the same thing in place.

## How a request flows

`POST /v1/events` end to end:

1. `security.ApiKeyAuthenticationFilter` checks `X-API-Key` (`/v1/**` only;
   health, metrics, and Swagger stay public).
2. `events.EventsController.create` calls `request.validate()` — invalid input
   throws `api.InvalidRequestException`, which `api.ApiExceptionHandler` turns
   into `400 {"error": ...}`. Controllers never build error responses.
3. `events.EventIngestion.ingest` runs one transaction: `EventStore.append`
   inserts the event; `deliveries.DeliveryOutbox.enqueue` inserts one pending
   delivery per matching subscription. Crash anywhere = both or neither.
4. Asynchronously, `deliveries.WebhookDispatcher` (a `@Scheduled` poller)
   claims due deliveries, signs the body (`X-EventCore-Signature`, HMAC via
   `crypto.HmacSha256`), POSTs it, and records the attempt; failures back off
   exponentially until the retry budget (`gives_up_after`) is spent.

## Adding a feature (the house method)

Every feature here was built the same way — copy it:

1. **Red**: write a failing integration test first. Extend
   `dev.eventcore.IntegrationTestBase` (gives you `api()` — an authenticated
   RestClient — `anonymousApi()`, `serverPort()`, `wipeAllData()`, and one
   shared Testcontainers TimescaleDB for the whole suite). Test names read as
   sentences: `aRevokedKeyStopsAuthenticating`.
2. **Migration**: next `V<N>__<what>.sql` in
   [`backend/src/main/resources/db/migration`](../backend/src/main/resources/db/migration).
   Migrations are append-only; never edit an applied one.
3. **Green**: request record with `validate()` → store method with the SQL →
   controller method. Error paths throw the `api` exceptions; new status codes
   need a handler entry in `ApiExceptionHandler`.
4. **Swagger**: one `@Operation(summary = ...)` per endpoint, `@Tag` per
   controller.
5. **Verify like a user**: `docker compose up --build -d` and curl it —
   or add a step to [`scripts/walkthrough.sh`](../scripts/walkthrough.sh).
6. Update the README API table and, if behavior changed, the
   [walkthrough](walkthrough.md).

## Running things

```bash
cd backend && ./mvnw test      # full suite (Docker required; ~1 min)
./mvnw test -Dtest=SmokeTest   # one class (from backend/)
docker compose up --build -d   # run the product (from the repo root)
./scripts/walkthrough.sh       # assert the product end-to-end (from the repo root)
```

More detail in the [testing guide](testing/README.md).

## Things that will surprise you

- **Spring Boot 4 ships Jackson 3** (`tools.jackson.*`). There is no
  `com.fasterxml` `ObjectMapper` bean; its exceptions are unchecked.
- **No `RestClient.Builder` bean** is auto-configured — build clients with
  `RestClient.create()` or your own factory (see `WebhookDispatcher`).
- **Hypertables can't be foreign-key targets**, which is why deliveries
  snapshot the event body instead of joining back to `events`.
- **Keyset pagination everywhere**: `api.Cursor` encodes `(time, id)`;
  no OFFSET, ever. Newest-first for humans, oldest-first for pull consumers.
- **Test data must not be dispatcher-food**: seed `webhook_deliveries` rows
  with `next_attempt_at` in the future or the live poller will claim them
  mid-test.

## Product context

Why features exist is documented, not folklore: jobs and outcomes in
[`docs/product/jobs.yaml`](product/jobs.yaml), competitive evidence in
[`docs/product/market-positioning.md`](product/market-positioning.md), and
per-feature decisions under [`docs/feature/`](feature/).
