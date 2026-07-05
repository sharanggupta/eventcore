# Data handling in EventCore

How EventCore treats data, mapped to the features that enforce it. This page
is factual (it describes the shipped product); the DPA and subprocessor pages
are templates awaiting the managed-hosting launch.

## What EventCore stores

| Data | Where | Protection |
|---|---|---|
| Event payloads (whatever you send) | `events` hypertable | You control content; retention configurable |
| Delivery bodies (payload snapshots per subscription) | `webhook_deliveries` | Payload minimization available (below); retention configurable |
| Delivery attempt records (status, error, 512-byte response snippet) | `delivery_attempts` | Rotates with delivery history |
| API keys | `api_keys` | **SHA-256 hash only** — plaintext shown once at issuance, never stored |
| Webhook signing secrets | `webhook_subscriptions` | Stored to sign deliveries; never exposed after registration |

## Controls that matter for GDPR

- **Storage limitation (Art. 5(1)(e))**: retention policies —
  `RETENTION_EVENTS_MAX_AGE` and `RETENTION_DELIVERY_HISTORY_MAX_AGE` — rotate
  old data automatically. Default is keep-forever; the operator chooses.
- **Data minimisation (Art. 5(1)(c))**: per-subscription `payloadFields`
  allow-lists mean each downstream consumer receives only the payload fields
  it needs — a card-processing consumer never sees user emails, and the
  delivery snapshot stores only the minimized body.
- **Right to erasure support**: events are queryable by type and time for
  location; the data-exit tooling (`scripts/export.sh` / `restore.sh`) covers
  portability (Art. 20) — one command produces a complete, restorable bundle.
- **Integrity**: webhook deliveries are HMAC-SHA256 signed; receivers verify
  before trusting.
- **Access control**: all data endpoints require API keys (revocable
  instantly, hashed at rest); key management requires a separate admin token.

## What EventCore does not do

No analytics, no telemetry, no phone-home: a self-hosted instance talks only
to your database and your registered webhook endpoints.
