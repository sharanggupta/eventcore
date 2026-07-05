# Slice 04 — Bulk redelivery

**Story**: US-04 | **job_id**: JOB-001 | **Outcomes**: O2, O3 | **Effort**: 0.5 day
**Release**: 1 — "Explain and recover any failed delivery" | **Priority**: 4 (score 12.0, but
sequenced after slice-03 by dependency)

## Goal

After a canary confirms the fix, one call requeues an outage's entire dead-letter backlog:
`POST /v1/deliveries/redeliver` with an explicit filter, returning `{"requeued": N}`.

## IN scope

- `POST /v1/deliveries/redeliver`, body requires `"status":"failed"` explicitly (D8 — no
  implicit "requeue everything"), optional `subscription_id`
- Response 202 `{"requeued": N}`; `N = 0` allowed (idempotent re-runs)
- Same per-delivery lifecycle as slice-03 (fresh cycle, cumulative attempts)

## OUT scope

- Id-list selection (returns only if this slice's hypothesis fails)
- Filters beyond status + subscription_id (e.g., time windows) — DESIGN may add if evidence
- Async job semantics / progress reporting — synchronous count is the contract at this scale

## Learning hypothesis

Real recovery is outage-shaped: failures cluster per subscription (or instance-wide), so a
filter is the natural selection unit.
**Disproves if it fails**: operators need arbitrary cherry-picking (id lists) because failures
are scattered and heterogeneous — reopen D8.
**Confirms if it succeeds**: filter + count is the complete bulk-recovery contract; the
`{"requeued":N}` ↔ dead-letter-count reconciliation becomes the incident close-out ritual.

## Acceptance criteria

- [ ] 136 failed billing-sync deliveries + matching bulk call → 202 `{"requeued":136}`; the
      failed list for that subscription drains to empty once the dispatcher runs
- [ ] Re-run with nothing matching → 202 `{"requeued":0}` (no double-queuing of pending rows)
- [ ] Omitting `subscription_id` requeues failed deliveries across all subscriptions
- [ ] Body without `status` → 400 `{"error":"status is required and must be \"failed\""}`
- [ ] **Dogfood (production data)**: on compose, dead-letter ≥3 real deliveries across a
      broken consumer, bulk-recover, reconcile count vs list — same day

## Dependencies

- slice-03 (redelivery lifecycle and eligibility guard) — hard dependency

## Effort estimate & reference class

0.5 day. Reference: slice-03's transition applied set-wise (single UPDATE … WHERE + count);
endpoint and validation follow existing conventions.

## Pre-slice SPIKE

None. If dead-letter populations are expected >10k, DESIGN revisits batching — not a DISCUSS
concern at first-party scale.
