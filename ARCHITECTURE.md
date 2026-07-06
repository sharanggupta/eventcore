# Architecture

EventCore is a permanent, append-only event log wrapped in two delivery
mechanisms: a transactional-outbox pipeline that pushes signed webhooks (and
retries until they land), and durable pull cursors that let any consumer walk
the log at its own pace and rewind.

The backend is package-by-feature under
[`backend/src/main/java/dev/eventcore`](backend/src/main/java/dev/eventcore):

- **`events`** — ingest, query (type/time/payload), the append+fan-out transaction, and the `EventSink` outbound port
- **`webhooks`** — subscription lifecycle: register, filter, delete
- **`deliveries`** — the outbox, the scheduled dispatcher, attempt history, redelivery
- **`pull`** — named durable cursors: fetch, commit, rewind, fleet lag
- **`security`** — API keys (SHA-256 hashed, shown once) and the `X-API-Key` filter
- **`metrics`** — the `/metrics` Prometheus text
- **`retention`** — the optional rotation sweeper (keep-forever by default)
- **`api`** / **`crypto`** — shared primitives the capabilities point inward to

This file is a signpost. The full picture lives in two single sources of truth:

- **[docs/architecture.md](docs/architecture.md)** — the diagrams as code (Mermaid): system overview, the life of an event, pull consumption, the component DAG, and the data model.
- **[docs/development.md](docs/development.md)** — the code map, how a request flows, and the house method for adding a feature.
