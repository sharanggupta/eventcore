# EventCore

Self-hosted event/audit logging system.

## Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)
- Maven 3.9+ (for local development)

## Quick Start

### Run with Docker Compose

```bash
# Start the full system
docker compose up

# Stop the system
docker compose down

# Stop and remove volumes
docker compose down -v
```

The application will be available at `http://localhost:8080`.

### Verify System

```bash
# Health check
curl http://localhost:8080/health
```

Should return: `OK`

## API

All `/v1/*` endpoints except key issuance require an API key in the
`X-API-Key` header. Issue one with the admin token from `.env`:

```bash
curl -X POST http://localhost:8080/v1/api-keys \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name": "my-service"}'
```

The response contains the plaintext key **once**; only its SHA-256 hash is
stored. Requests without a valid key receive `401`.

### Ingest an event

```bash
curl -X POST http://localhost:8080/v1/events \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"type": "user.created", "payload": {"userId": "42"}}'
```

Returns `201` with the stored event's `id` and `time`. `type` is required;
`payload` is optional arbitrary JSON. A missing or blank `type` returns `400`.

### List events

```bash
curl -H "X-API-Key: $API_KEY" 'http://localhost:8080/v1/events?limit=50'
```

Returns the newest events first as `{"items": [...], "nextCursor": "..."}`.
Pass `nextCursor` back as `cursor` to fetch the next page; it is `null` on the
last page. `limit` defaults to 50 (max 200).

### Register a webhook

```bash
curl -X POST http://localhost:8080/v1/webhooks \
  -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/hooks/eventcore"}'
```

Every ingested event is POSTed as JSON (`{id, time, type, payload}`) to each
registered webhook. Failed deliveries are retried with exponential backoff up
to 5 attempts, tracked in a database outbox so pending deliveries survive
restarts. `GET /v1/webhooks` lists registered subscriptions.

## Local Development

### Run Tests

```bash
./mvnw test
```

Tests use Testcontainers and require Docker to be running.

### Build

```bash
./mvnw clean package
```

## Configuration

Environment variables (configured in `.env`):

| Variable | Default | Description |
|----------|---------|-------------|
| DB_NAME | eventcore | Database name |
| DB_USER | eventcore | Database user |
| DB_PASSWORD | eventcore | Database password |
| DB_PORT | 5432 | Database port |
| SERVER_PORT | 8080 | Application port |
| ADMIN_TOKEN | (unset) | Token for issuing API keys; issuance is disabled while unset |

## Stack

- Java 21
- Spring Boot 4.0.6
- TimescaleDB (PostgreSQL 16)
- Flyway (migrations)
