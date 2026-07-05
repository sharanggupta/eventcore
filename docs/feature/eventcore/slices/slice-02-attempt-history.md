# Slice 02 — Per-attempt delivery history

**Story**: US-02 | **job_id**: JOB-001 | **Outcomes**: O3 (primary), O1 | **Effort**: 1 day
**Release**: 1 — "Explain and recover any failed delivery" | **Priority**: 2

## Goal

Every delivery attempt is recorded (status code or transport error, response snippet, duration,
timestamp) and readable via `GET /v1/deliveries/{id}` — the "why did it fail?" call (O3, the
job's single highest-opportunity outcome at 15.0).

## IN scope

- Persist per-attempt record at dispatch time: attempt number, `attempted_at`, `status_code`
  (nullable), `error` (nullable), `response_snippet` (≤ 512 bytes), `duration_ms`
- `GET /v1/deliveries/{id}` → delivery + `delivery_attempts` array ordered by attempt
- Additive migration; legacy deliveries return `delivery_attempts: []` with the old count
- 404 typed error for unknown ids

## OUT scope

- Full response bodies / headers capture (snippet only; revisit if hypothesis fails)
- Attempt-record retention/pruning policy — **flagged as DESIGN constraint** (growth must be
  bounded), not decided here
- Listing attempts across deliveries (no aggregate attempt query)

## Learning hypothesis

Failure cause is diagnosable from status code + error class + a 512-byte response snippet.
**Disproves if it fails**: snippet-level capture is insufficient and operators need full
request/response forensics (headers, full bodies) — a much heavier store.
**Confirms if it succeeds**: lightweight capture answers O3; the same records later serve as
the audit substrate for replay (DIVERGE Option 2 compatibility note).

## Acceptance criteria

- [ ] A delivery that got 503 twice then connection-refused three times shows exactly that
      5-row sequence with timestamps and durations
- [ ] A consumer 400 with body `{"error":"unknown signature"}` yields a snippet containing
      "unknown signature"
- [ ] Timeout attempt: `status_code: null`, `error: "timeout after 10s"`, `duration_ms: 10000`
- [ ] Pre-upgrade delivery: legacy `attempts` count + empty `delivery_attempts`, no error
- [ ] Unknown id → 404 `{"error":"delivery not found"}`
- [ ] **Dogfood (production data)**: on compose, script the local consumer to 503 twice then
      die; the elevator-pitch curl shows that exact story — same day

## Dependencies

None hard (journey-pairs with slice-01). Slice-03's attempt-6 evidence builds on this capture.

## Effort estimate & reference class

1 day. Reference: existing outbox dispatcher (`@Scheduled`) — capture hooks into its existing
attempt loop; store follows existing JdbcClient + Testcontainers patterns.

## Pre-slice SPIKE

None — dispatcher code paths are known; only decision (snippet size) is locked at 512 bytes.
