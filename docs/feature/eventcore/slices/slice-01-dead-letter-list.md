# Slice 01 — Dead-letter list

**Story**: US-01 | **job_id**: JOB-001 | **Outcomes**: O3 (primary), O1 | **Effort**: 0.5 day
**Release**: 1 — "Explain and recover any failed delivery" | **Priority**: 1 (score 25.0)

## Goal

An operator lists every dead-lettered (and pending/delivered) delivery via
`GET /v1/deliveries`, filterable by status, subscription, and event — no database shell.

## IN scope

- `GET /v1/deliveries` with `status`, `subscription_id`, `event_id` filters
- Cursor pagination consistent with `GET /v1/events`
- Response items: `id`, `event_id`, `subscription_id`, `status`, `attempts`,
  `next_attempt_at`, `created_at` (all from existing `webhook_deliveries` columns)
- `X-API-Key` auth, typed errors (400 invalid status, 401 keyless)

## OUT scope

- Per-attempt detail (slice-02)
- Any mutation (redelivery — slices 03/04)
- Time-range or payload filters (Forensic Search direction, out of feature scope)

## Learning hypothesis

A list built from *existing* outbox columns already shortens 3am triage.
**Disproves if it fails**: (a) the assumption that dead-letter pain is acute for first-party
operators (recommendation assumption (a)) — if nobody consults the list, re-open the DIVERGE
dissent; (b) "operators need rich attempt detail before any visibility is useful" — if the list
alone resolves triage, slice-02's value claim strengthens for *diagnosis*, not *triage*.
**Confirms if it succeeds**: deliveries-as-a-resource is the right read model shape.

## Acceptance criteria

- [ ] `GET /v1/deliveries?status=failed&subscription_id={id}` returns exactly the matching
      dead letters, cursor-paginated
- [ ] `?status=pending` items expose `next_attempt_at` (retrying vs abandoned is visible)
- [ ] `?event_id={id}` answers "did this event reach every subscriber?"
- [ ] `?status=exploded` → 400 `{"error":"status must be one of pending, delivered, failed"}`;
      keyless → 401 typed error
- [ ] **Dogfood (production data)**: on the running compose stack, point a subscription at a
      consumer that returns 500, ingest real events until deliveries dead-letter, and surface
      them with the elevator-pitch curl — same day

## Dependencies

None. First slice; everything downstream in the journey consumes its `${delivery_id}` output.

## Effort estimate & reference class

0.5 day. Reference: existing `GET /v1/events` listing (cursor pagination, JdbcClient store,
record contract) — this is the same shape over `webhook_deliveries`.

## Pre-slice SPIKE

None — no unknowns; read-only over existing schema.
