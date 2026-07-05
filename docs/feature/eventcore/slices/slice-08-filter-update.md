# Slice 08 — Filter update in place

**Story**: US-08 | **job_id**: JOB-001 | **Outcomes**: O6 | **Effort**: 0.5 day
**Release**: 3 — "Right-size each subscription's stream" | **Priority**: 8 (after slice-07 by
dependency)

## Goal

`PATCH /v1/webhooks/{id}` changes a subscription's `event_types` without changing its id or
HMAC secret — evolving a consumer's event diet stops requiring a cross-team secret-rotation
ceremony.

## IN scope

- `PATCH /v1/webhooks/{id}` with `{"event_types": [...]}` replaces the filter;
  `{"event_types": null}` clears it (back to receive-everything)
- `id` and `secret` survive PATCH — proven end-to-end by HMAC signatures still verifying on
  the next delivery
- Change applies to events ingested after the PATCH only (no retroactive deliveries — that
  would be replay, O8, out of scope)
- `[]` → 400 typed error (same rule as creation); unknown id → 404
  `{"error":"subscription not found"}`

## OUT scope

- PATCHing `url` or any other field — `event_types` only in this slice
- Retroactive delivery of past events after widening a filter (O8 / replay territory)
- Subscription pause/disable

## Learning hypothesis

Filters change often enough over a subscription's life that in-place update (secret-preserving)
earns its keep.
**Disproves if it fails**: delete+recreate was tolerable and PATCH was overbuilt — a cheap
0.5-day lesson that caps further subscription-mutation investment.
**Confirms if it succeeds**: subscriptions are living configuration; DESIGN should expect
future PATCH surface (url, pause) demand.

## Acceptance criteria

- [ ] Widening warehouse-notify's filter to add return.received returns the same subscription
      id with the updated list
- [ ] The next return.received event is delivered and its HMAC signature verifies against the
      pre-PATCH secret (secret provably unchanged)
- [ ] A return.received event ingested *before* the PATCH produces no delivery
- [ ] `{"event_types": null}` → subscription receives all types again
- [ ] Unknown id → 404 typed error; `[]` → 400 typed error
- [ ] **Dogfood (production data)**: on compose, widen a live filter and watch the next
      matching event arrive signature-valid — same day

## Dependencies

- slice-07 (`event_types` exists on subscriptions) — hard dependency

## Effort estimate & reference class

0.5 day. Reference: slice-07's column + existing webhook endpoints; one guarded UPDATE and a
contract record.

## Pre-slice SPIKE

None.
