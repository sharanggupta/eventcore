# Slice 05 — Pipeline health metrics

**Story**: US-05 | **job_id**: JOB-001 | **Outcomes**: O5, O3 | **Effort**: 1 day
**Release**: 2 — "See pipeline health and silence" | **Priority**: 5

## Goal

`GET /metrics` (Prometheus text, unauthenticated like `/health`) exposes pipeline truth —
delivery counts by status, backlog age, ingest and attempt counters — so the operator's
existing monitoring stack sees trouble before downstream teams do.

## IN scope

- `GET /metrics`, Prometheus text exposition format (D4), no `X-API-Key`
- Gauges (database truth): `eventcore_deliveries{status="pending|delivered|failed"}` (all
  three series always present, zero-valued if empty),
  `eventcore_oldest_pending_delivery_age_seconds`
- Counters (process-lifetime, `rate()`-friendly): `eventcore_events_ingested_total`,
  `eventcore_delivery_attempts_total{result="success"|"failure"}`

## OUT scope

- Per-event-type freshness series (slice-06)
- JSON format variant (deferred; D4 alternative note)
- Auth/network hardening for `/metrics` — DESIGN/DEVOPS guidance, flagged in constraints
- Grafana dashboards (operator-side; docs may link an example later)

## Learning hypothesis

Prometheus text at `/metrics` plugs into the adopter's existing stack with zero EventCore-side
alerting machinery.
**Disproves if it fails**: first-party adopters lack a Prometheus-compatible stack → the D3
delegation bet is wrong and built-in monitors (deferred scope) move back on the table.
**Confirms if it succeeds**: EventCore's observability strategy is "expose signals, delegate
alerting" — cheap to extend, nothing to operate.

## Acceptance criteria

- [ ] `curl http://localhost:8080/metrics` returns valid Prometheus text exposition
- [ ] With 137 failed / 12 pending / 4,891 delivered rows, the three gauges match those counts
- [ ] Fresh instance: every documented series present with value 0 (no absent-metric alert holes)
- [ ] Pending rows + dispatcher stopped 5 min → `oldest_pending_delivery_age_seconds ≥ 300`
- [ ] `/metrics` serves 200 keyless while `/v1/events` keyless still returns the typed 401
- [ ] **Dogfood (production data)**: scrape the compose instance during a forced failure burst;
      failed gauge tracks `GET /v1/deliveries?status=failed` — same day

## Dependencies

None — derives from existing tables (deliberately independent of slices 01–04; attempt counter
uses dispatcher outcomes, not slice-02's store).

## Effort estimate & reference class

1 day. Reference: `/health` endpoint (existing unauthenticated surface) + straightforward
aggregate queries over `webhook_deliveries`/`events`; text rendering is mechanical.

## Pre-slice SPIKE

None. (Spring Boot 4 metrics tooling choice — Micrometer vs hand-rendered — is a DESIGN
decision; the contract here is format + series names only.)
