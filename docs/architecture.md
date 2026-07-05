# Architecture

All diagrams on this page are [Mermaid](https://mermaid.js.org/) — plain text
in this file, rendered automatically by GitHub, versioned and reviewed like
any other code. That is the whole "architecture as code" toolchain: there is
deliberately no generation step (no Maven plugin, no image exports to drift
out of date). If a diagram is wrong, the fix is a one-line PR.

## System overview

Producers push events in; EventCore records them durably and fans them out —
push (signed webhooks, retried) and pull (durable cursors). Everything
observable reads the same API.

```mermaid
flowchart LR
    subgraph producers["Your systems"]
        P1["orders service"]
        P2["any producer"]
    end

    subgraph eventcore["EventCore instance"]
        API["REST API<br/>/v1/*"]
        DB[("TimescaleDB<br/>events hypertable + outbox")]
        DISP["WebhookDispatcher<br/>(scheduled)"]
        RET["RetentionSweeper<br/>(daily, optional)"]
        API --> DB
        DISP --> DB
        RET --> DB
    end

    subgraph consumers["Downstream"]
        WH["webhook receivers<br/>(verify HMAC)"]
        PULL["pull consumers<br/>(fetch / commit / rewind)"]
    end

    subgraph observers["Observability"]
        DASH["dashboard (Next.js)"]
        PROM["Prometheus"]
    end

    P1 -- "POST /v1/events<br/>X-API-Key" --> API
    P2 --> API
    DISP -- "signed POST<br/>X-EventCore-Signature" --> WH
    PULL -- "GET .../events + commit" --> API
    DASH -- "server-side, key in env" --> API
    PROM -- "GET /metrics (public)" --> API
```

## The life of an event

The critical design decision is visible in the first three arrows: the event
insert and its fan-out rows are **one transaction** (the outbox pattern), so a
crash can never record an event without queueing its deliveries, or vice
versa. Delivery then happens asynchronously with retries.

```mermaid
sequenceDiagram
    autonumber
    participant Producer as orders service
    participant API as EventsController
    participant TX as EventIngestion (one transaction)
    participant DB as TimescaleDB
    participant Disp as WebhookDispatcher
    participant Recv as webhook receiver

    Producer->>API: POST /v1/events {type, payload}
    API->>TX: ingest(type, payload)
    TX->>DB: INSERT event
    TX->>DB: INSERT one pending delivery per matching subscription (payload minimized per allow-list)
    TX-->>Producer: 201 {id, time, type}

    loop every poll-interval
        Disp->>DB: claim due pending deliveries
        Disp->>Recv: POST body + X-EventCore-Signature (HMAC-SHA256)
        alt 2xx
            Disp->>DB: delivered + attempt recorded
        else failure
            Disp->>DB: attempt recorded, backoff doubles - failed after 5
        end
    end

    Note over Recv,DB: failed? inspect attempts via GET /v1/deliveries/{id},<br/>then POST .../redeliver for a fresh cycle
```

## Pull consumption (replay)

Push is EventCore calling you; pull is you walking the permanent log with a
named durable cursor — at-least-once, crash-safe, rewindable.

```mermaid
sequenceDiagram
    autonumber
    participant C as consumer
    participant API as PullSubscriptionsController
    participant DB as events log

    C->>API: POST /v1/pull-subscriptions {name, from: beginning}
    loop until items is empty
        C->>API: GET /{name}/events?limit=100
        API->>DB: oldest-first after cursor (peek - nothing advances)
        API-->>C: {items, nextCursor}
        C->>C: process batch (idempotently)
        C->>API: POST /{name}/commit {cursor}
    end
    Note over C,API: crash anywhere - restart resumes from the committed<br/>position, and POST /{name}/rewind replays history
```

## Backend components

Package-by-feature; arrows are the only cross-package dependencies. Shared
primitives (`api`, `crypto`) depend on nothing domain-shaped.

```mermaid
flowchart TD
    events["events<br/>ingest, query, readers"]
    webhooks["webhooks<br/>subscription lifecycle"]
    deliveries["deliveries<br/>outbox, dispatcher, redelivery"]
    pull["pull<br/>durable cursors"]
    security["security<br/>API keys, auth filter"]
    metrics["metrics<br/>/metrics text"]
    retention["retention<br/>rotation sweeper"]
    api["api<br/>errors, Cursor, OpenAPI"]
    crypto["crypto<br/>Sha256, HmacSha256, Secrets"]

    events --> deliveries
    events --> api
    webhooks --> api
    webhooks --> crypto
    webhooks --> events
    deliveries --> api
    deliveries --> crypto
    pull --> events
    pull --> api
    security --> api
    security --> crypto
```

`events → deliveries` is the transactional fan-out; `pull → events` is the
oldest-first reader; `webhooks → events` reuses the type-filter vocabulary.

## Data model

```mermaid
erDiagram
    events {
        uuid id PK
        timestamptz time PK "hypertable partition"
        text type
        jsonb payload
    }
    webhook_subscriptions {
        uuid id PK
        text url
        text secret "signs deliveries"
        jsonb event_types "null = all"
        jsonb payload_fields "null = full payload"
    }
    webhook_deliveries {
        uuid id PK
        uuid event_id
        uuid subscription_id FK
        jsonb body "minimized snapshot"
        text status "pending | delivered | failed"
        int attempts
        int gives_up_after "redelivery raises this"
        timestamptz next_attempt_at
    }
    delivery_attempts {
        uuid delivery_id FK
        int attempt
        int status_code "null on transport error"
        text error
        text response_snippet "512 bytes"
        bigint duration_ms
    }
    api_keys {
        uuid id PK
        text key_hash "SHA-256 only"
        timestamptz revoked_at "null = active"
    }
    pull_subscriptions {
        text name PK
        timestamptz position_time "null = beginning"
        uuid position_id
        jsonb event_types
    }

    webhook_subscriptions ||--o{ webhook_deliveries : "fan-out (cascade)"
    webhook_deliveries ||--o{ delivery_attempts : "records (cascade)"
```

Two deliberate non-links: `webhook_deliveries.event_id` has no foreign key
(TimescaleDB hypertables cannot be FK targets — the body snapshot makes the
delivery self-contained), and `pull_subscriptions` references the log only by
cursor position.

## Deployment (local / self-hosted)

```mermaid
flowchart LR
    subgraph compose["docker compose"]
        APP["app :8080<br/>(backend/Dockerfile)"]
        PG[("db :5432<br/>timescale/timescaledb-pg16<br/>volume: eventcore_data")]
        APP --> PG
    end
    DASHDEV["dashboard :3000<br/>npm run dev"] --> APP
    DEMO["orders demo :8081<br/>backend/mvnw -f examples/..."] --> APP
    APP -- "host.docker.internal" --> DEMO
```

The managed-hosting variants (instance-per-customer on Hetzner, control
plane, billing) are analyzed in
[product/deployment-architecture.md](product/deployment-architecture.md) —
pending the founder's hosting decision.
