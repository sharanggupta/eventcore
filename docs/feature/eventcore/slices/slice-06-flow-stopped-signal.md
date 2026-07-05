# Slice 06 — Flow-stopped signal

**Story**: US-06 | **job_id**: JOB-001 | **Outcomes**: O5 (primary) | **Effort**: 0.5 day
**Release**: 2 — "See pipeline health and silence" | **Priority**: 6 (after slice-05 by
dependency)

## Goal

Producer silence becomes detectable within one 15-minute alert window: per-event-type
last-received timestamps on `/metrics` plus a documented, copy-pasteable Prometheus alert rule.
This is the honest thin slice of "flow monitors" (D3).

## IN scope

- `eventcore_event_last_received_timestamp_seconds{type="…"}` for every event type seen since
  startup (timestamp, not age — PromQL computes age via `time() - series`)
- Documentation: the alert rule
  `time() - eventcore_event_last_received_timestamp_seconds{type="order.placed"} > 900`
  plus the `absent()` recipe for must-always-exist types
- Cardinality guidance in docs (intended for first-party type counts, ≤ ~100)

## OUT scope (the honest carve — feature-delta Out-of-Scope §1)

- Built-in monitor resources: monitor CRUD, expected-cadence configuration, notification
  channels. Rationale: the persona already runs Prometheus; EventCore supplies the signal only
  it has, and delegates the commodity alerting engine. **Re-open condition**: this slice's
  hypothesis fails.
- Per-producer (API-key-level) freshness — type-level only in this feature

## Learning hypothesis

Per-type last-seen + a documented rule detects stopped flow in ≤15 min with zero new
machinery.
**Disproves if it fails**: adopters can't or won't run the alert side themselves → built-in
monitors (deferred) become the next slice of O5.
**Confirms if it succeeds**: O5 (12.5) is served at 0.5 day cost; KPI-3 becomes measurable.

## Acceptance criteria

- [ ] After ingesting order.placed, invoice.paid, refund.issued: three freshness series exist
- [ ] Last order.placed at 14:05; scrape at 14:21 still reports the 14:05 timestamp, and the
      documented rule's expression evaluates true
- [ ] Values update on every ingest of that type; unrelated types don't move
- [ ] Docs contain the copy-pasteable rule (15-min default) and the `absent()` recipe
- [ ] **Dogfood (production data)**: on compose, seed a type once per minute, stop the seeder,
      verify the rule expression flips true after the window — same day

## Dependencies

- slice-05 (`/metrics` endpoint exists) — hard dependency

## Effort estimate & reference class

0.5 day. Reference: slice-05's gauge pattern + one `MAX(created_at) GROUP BY type` style
aggregate over the existing events hypertable.

## Pre-slice SPIKE

None.
