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

### Ingest an event

```bash
curl -X POST http://localhost:8080/v1/events \
  -H 'Content-Type: application/json' \
  -d '{"type": "user.created", "payload": {"userId": "42"}}'
```

Returns `201` with the stored event's `id` and `time`. `type` is required;
`payload` is optional arbitrary JSON. A missing or blank `type` returns `400`.

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

## Stack

- Java 21
- Spring Boot 4.0.6
- TimescaleDB (PostgreSQL 16)
- Flyway (migrations)
