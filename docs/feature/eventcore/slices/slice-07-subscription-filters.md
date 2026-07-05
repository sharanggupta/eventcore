# Slice 07 — Per-subscription event-type filters

**Story**: US-07 | **job_id**: JOB-001 | **Outcomes**: O6 (primary) | **Effort**: 1 day
**Release**: 3 — "Right-size each subscription's stream" | **Priority**: 7

## Goal

A subscription can be created with an `event_types` list and then receives only matching
events — noise stops, and sensitive types stop crossing boundaries they shouldn't (O6).

## IN scope

- `POST /v1/webhooks` accepts optional `event_types` (non-empty array of exact-match strings);
  creation response and `GET /v1/webhooks` echo it (`null` = unfiltered)
- Dispatcher creates outbox rows only for matching types on filtered subscriptions —
  evaluation at delivery-creation time (implies D7 "future events only")
- Back-compat: omitted/`null` = all events; existing rows untouched (additive migration)
- `[]` → 400 `{"error":"event_types must be null or a non-empty array"}` (D7: "subscribe to
  nothing" is refused, not silently honored)

## OUT scope

- Updating filters on existing subscriptions (slice-08)
- Wildcard/prefix patterns (`order.*`) — exact match only until evidence demands more
  (feature-delta Alternatives, D7)
- Type registry/validation — types remain free-form strings

## Learning hypothesis

Exact-match lists cover real first-party filtering needs (persona reality: 14 types, 4
consumers).
**Disproves if it fails**: immediate demand for patterns/wildcards → D7 semantics reopen with
evidence in hand.
**Confirms if it succeeds**: KPI-4 (0 non-matching deliveries) is achievable with the simplest
possible semantics.

## Acceptance criteria

- [ ] Subscription filtered to ["order.placed","refund.issued"]: ingesting order.placed,
      user.signed_up, invoice.paid creates a delivery for order.placed only
- [ ] `GET /v1/deliveries?subscription_id={id}` never shows a non-matching type for it
- [ ] Pre-existing/unfiltered subscriptions still receive every event (zero upgrade change)
- [ ] `GET /v1/webhooks` shows `event_types` per subscription (`null` when unfiltered)
- [ ] Filtering on a never-yet-seen type is accepted
- [ ] `"event_types": []` → 400 typed error
- [ ] **Dogfood (production data)**: on compose, filtered + unfiltered subscriptions side by
      side receive 1 and 3 deliveries from a 3-type seed batch — same day

## Dependencies

None (independent of slices 01–06).

## Effort estimate & reference class

1 day. Reference: existing dispatcher fan-out (outbox insert per subscription) — adds one
predicate; migration adds one nullable column; contract records extended.

## Pre-slice SPIKE

None.
