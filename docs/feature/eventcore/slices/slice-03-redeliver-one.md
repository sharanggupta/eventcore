# Slice 03 — One-call redelivery

**Story**: US-03 | **job_id**: JOB-001 | **Outcomes**: O3, O2 | **Effort**: 1 day
**Release**: 1 — "Explain and recover any failed delivery" | **Priority**: 3

## Goal

One call — `POST /v1/deliveries/{id}/redeliver` — returns a dead-lettered delivery to the
outbox lifecycle for a fresh retry cycle. Recovery becomes a verb; the production `UPDATE`
dies.

## IN scope

- `POST /v1/deliveries/{id}/redeliver` on a `failed` delivery → 202, status `pending`,
  `next_attempt_at` = now; existing dispatcher handles it with normal HMAC/retry semantics (D5)
- Attempt history cumulative across redeliveries (attempt numbers continue: 6, 7, …)
- Eligibility guard: only `failed` → otherwise 409 typed error; unknown id → 404

## OUT scope

- Bulk redelivery (slice-04)
- Redelivering `delivered` deliveries / replay of history (O8, DIVERGE Option 2 — out of
  feature scope with rationale in feature-delta Out-of-Scope §2)
- Custom retry budgets per redelivery (fresh standard cycle only)

## Learning hypothesis

Re-enqueue through the existing outbox lifecycle is sufficient recovery — operators accept
eventually-consistent confirmation (poll the detail endpoint seconds later).
**Disproves if it fails**: operators demand synchronous send-and-confirm, forcing a second
delivery path (rejected alternative in feature-delta D5) — significant redesign signal.
**Confirms if it succeeds**: one delivery path stays the single source of delivery truth, and
the canary-then-bulk recovery pattern is viable.

## Acceptance criteria

- [ ] Redeliver a failed delivery → 202 `{"id":…,"status":"pending","next_attempt_at":…}`;
      within the dispatcher interval it becomes `delivered`, attempt 6 records `status_code: 200`
- [ ] Consumer still broken → fresh 5-attempt cycle (attempts 6–10 recorded) → `failed` again;
      no infinite loop
- [ ] `delivered` delivery → 409 `{"error":"only failed deliveries can be redelivered"}`
- [ ] Unknown id → 404 `{"error":"delivery not found"}`
- [ ] **Dogfood (production data)**: on compose — kill consumer, dead-letter a real delivery,
      revive consumer, redeliver via the elevator-pitch curl, watch `delivered` — same day

## Dependencies

None hard. Journey depends on slice-01 (to find `${delivery_id}`) and slice-02 (attempt-6
evidence); mechanically independent.

## Effort estimate & reference class

1 day. Reference: existing outbox state machine — this adds one guarded state transition
(`failed → pending`) plus an endpoint; race-safety with in-flight dispatch is a named DESIGN
concern (failed-only eligibility is the requirements-level mitigation).

## Pre-slice SPIKE

None.
