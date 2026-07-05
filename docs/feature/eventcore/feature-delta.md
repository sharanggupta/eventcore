<!-- markdownlint-disable MD024 -->
# Feature Delta — eventcore (Pipeline Control Tower)

**Wave**: DISCUSS | **Agent**: Luna (nw-product-owner) | **Date**: 2026-07-05
**Upstream**: DIVERGE recommendation (`docs/feature/eventcore/recommendation.md`) — Pipeline
Control Tower, score 4.57. Job SSOT: `docs/product/jobs.yaml` (JOB-001, desk-validated).
**Density**: mode=full (Tier-1 [REF] + all applicable Tier-2 expansions rendered).

---

## Wave: DISCUSS / [REF] Persona ID

`platform-operator` — first-party platform/backend engineer operating their own EventCore
instance; terminal-native, curl/jq fluent, on-call, accountable for downstream integrations
staying consistent. Exemplar used in all examples: **Priya Raghavan**, platform engineer at
Nimbus Retail. SSOT: `docs/product/personas/platform-operator.yaml`.

## Wave: DISCUSS / [REF] JTBD One-liner

**JOB-001** (strategic, desk-validated): *When my software system performs actions that
customers, auditors, or dependent systems rely on, I want each action durably recorded and
conveyed to every party that depends on it, so I can prove what happened and keep dependents
consistent — without building event infrastructure myself.* This feature serves the
**propagation facet**: outcomes **O3** (15.0, diagnose delivery failure), **O5** (12.5, detect
flow stopped), **O6** (11.0, unwanted events), with **O1** (14.0, locate incident events) and
**O2** (13.0, never miss an event) served secondarily.

## Wave: DISCUSS / [REF] Locked Decisions

- **D1 — Assumption (a) affirmed**: the target adopter is a first-party team operating its own
  EventCore instance (persona `platform-operator`). No evidence surfaced to re-open the
  dissenting case (pull subscriptions). Verdict: proceed with Pipeline Control Tower.
- **D2 — Scope split (Elephant Carpaccio)**: recommendation scope flagged OVERSIZED (see Scope
  Assessment). Split into 8 thin end-to-end slices, ≤1 day each, every slice value-bearing.
  Autonomous call (subagent session), documented here.
- **D3 — Flow-stopped detection carved thin**: shipped as a per-event-type
  `last_received_timestamp` metric plus a documented Prometheus alert rule. Built-in monitor
  resources (monitor CRUD, notification channels) are OUT of scope — the persona already runs a
  Prometheus stack; duplicating its alerting engine is waste. Re-open if slice-06's hypothesis
  fails (adopters lack Prometheus).
- **D4 — Metrics contract**: `GET /metrics`, Prometheus text exposition format, unauthenticated
  (same trust level as `/health`). Rationale: persona's ecosystem lingua franca; JSON variant
  deferred. Network exposure guidance is a DESIGN/DEVOPS concern.
- **D5 — Redelivery semantics**: redelivery re-enqueues through the existing outbox lifecycle
  (status → `pending`, `next_attempt_at` → now, fresh retry cycle, attempt history keeps
  appending). No bypass send path. Only `failed` deliveries are eligible.
- **D6 — Deliveries become a first-class read resource**: `GET /v1/deliveries` (+`/{id}`) under
  the existing `X-API-Key` realm, cursor pagination consistent with `GET /v1/events`, typed
  errors `{"error": "..."}`.
- **D7 — Filter semantics**: `event_types` is an exact-match string list. Omitted/`null` = all
  events (back-compat for every existing subscription). Empty array `[]` = 400 typed error
  (ambiguity forbidden). Filter changes affect only future events — never retroactive.
- **D8 — Bulk redelivery selects by filter**, not id list: body requires `"status": "failed"`
  explicitly (no accidental full requeue), optional `subscription_id`; response reports
  `{"requeued": N}`. Arbitrary id-list selection deferred until slice-04's hypothesis says
  otherwise.
- **D9 — Telemetry emission skipped**: the nWave telemetry helper scripts
  (`scripts/shared/telemetry.py`, density event dataclass) do not exist in this repository. No
  `DocumentationDensityEvent` emitted; recorded here in lieu of JSONL.
- **D10 — Per-wave peer review skipped**: optional per skill; no trigger fired (single persona,
  no compliance terms, DoR unambiguous, DIVERGE already peer-approved). The mandatory
  consolidated review fires at end of DISTILL against this full feature-delta.

## Wave: DISCUSS / [REF] Scope Assessment (Elephant Carpaccio Gate)

**Verdict: OVERSIZED — split confirmed (autonomous).** Signals fired (2+ required):

| Signal | Finding |
|---|---|
| Multiple independent user outcomes shippable separately | YES — recovery (O3/O2), health visibility (O5), filtering (O6) are independent |
| >3 modules touched | Borderline — outbox/delivery, subscriptions, observability, plus API surface |
| Estimated effort | ~6 dev-days across 8 stories (under 2 weeks, but far over the 1–3 day story ceiling as a monolith) |
| >10 stories | No (8) |

**Split**: 8 thin vertical slices, each ≤1 day, each ending in a curl-able, dogfoodable
behavior on the running compose deployment. Slice briefs: `docs/feature/eventcore/slices/`.
`## Scope Assessment: SPLIT-CONFIRMED — 8 stories, 3 modules, estimated 6 days total, max 1 day/slice.`

## Wave: DISCUSS / [REF] Story Map

**User**: platform-operator (Priya) | **Goal**: when delivery breaks, see it, explain it, fix
it — and right-size what each dependent receives — without a database shell.

### Backbone

| Monitor health | Notice failure | Locate failures | Diagnose cause | Recover deliveries | Prevent recurrence |
|---|---|---|---|---|---|
| Scrape `/metrics` (S5) | Alert on failed-gauge spike (S5) | List failed deliveries (S1) | Inspect per-attempt history (S2) | Redeliver one (S3) | Filter subscription at creation (S7) |
| Watch per-type freshness (S6) | Alert on flow silence (S6) | Filter by subscription / event (S1) | Read response snippets (S2) | Bulk redeliver (S4) | Update filters in place (S8) |

### Walking Skeleton note (brownfield)

The ingest→store→deliver flow already runs end-to-end (44 green integration tests, compose
deployment) — no skeleton needed for the product. For **this feature's journey**, the skeleton
is the recovery spine **S1 → S2 → S3** (Locate → Diagnose → Recover). Intentional activity
gap: in Release 1, "Monitor/Notice" is still served by the existing painful path (a human
reports missing data) because you cannot alert on metrics that do not exist yet; the gap closes
in Release 2. Documented as accepted, not accidental.

### Releases (named by outcome, not feature)

- **Release 1 — "Explain and recover any failed delivery"** (S1–S4) → O3, O2 | KPI-1, KPI-2, KPI-5
- **Release 2 — "See pipeline health and silence"** (S5–S6) → O5 | KPI-3, KPI-5
- **Release 3 — "Right-size each subscription's stream"** (S7–S8) → O6 | KPI-4

### Prioritization (Value × Urgency / Effort; tie-break: skeleton > riskiest assumption > value)

| Order | Slice | Story | V | U | E | Score | Rationale |
|---|---|---|---|---|---|---|---|
| 1 | slice-01-dead-letter-list | US-01 | 5 | 5 | 1 | 25.0 | Cheapest test of the riskiest assumption (operator pain at dead letters); works on existing columns; unblocks the whole journey |
| 2 | slice-02-attempt-history | US-02 | 5 | 4 | 2 | 10.0 | The "why" of O3 (15.0); needs new capture, so second |
| 3 | slice-03-redeliver-one | US-03 | 5 | 4 | 2 | 10.0 | Recovery half of the journey; canary before bulk |
| 4 | slice-04-redeliver-bulk | US-04 | 4 | 3 | 1 | 12.0 | Scores above S2/S3 but depends on S3 mechanics — dependency wins |
| 5 | slice-05-pipeline-metrics | US-05 | 4 | 3 | 2 | 6.0 | O5 substrate; independent of S1–S4 |
| 6 | slice-06-flow-stopped-signal | US-06 | 4 | 3 | 1 | 12.0 | O5 (12.5) proper; depends on S5's endpoint |
| 7 | slice-07-subscription-filters | US-07 | 3 | 2 | 2 | 3.0 | O6 (11.0), lowest opportunity in scope |
| 8 | slice-08-filter-update | US-08 | 2 | 2 | 1 | 4.0 | Depends on S7; smallest |

### Carpaccio taste tests

| Test | Result |
|---|---|
| Any slice ships 4+ new components? | No — max 2 (table + endpoint) |
| Every slice depends on a new abstraction? | No — only S2 introduces the attempt-record store; S5 metrics derive from existing tables; S6 depends on S5's endpoint only |
| No slice disproves a pre-commitment? | S1 tests assumption (a); S5/S6 test the Prometheus-delegation bet (D3); S8 tests whether PATCH was worth building |
| Synthetic-data-only slices? | None — every slice's AC requires dogfood on the running compose stack with a real failing consumer (see slice briefs) |
| 2+ slices identical except scale? | Considered S3 vs S4: NOT merged — bulk adds selection semantics, an explicit-status safety contract, and a count response; merging breaks the ≤1-day rule. Documented per taste-test rule. |

## Wave: DISCUSS / [REF] User Stories

All stories: `job_id: JOB-001`, persona `platform-operator`. Effort is crafter dispatch time.

---

### US-01: See every dead-lettered delivery

`slice-01` | outcomes: **O3** (primary), O1 | effort: **0.5 day**

**Problem**: When a delivery exhausts its 5 retries, EventCore abandons it silently. Priya
Raghavan is a platform engineer at Nimbus Retail who gets paged when the billing team notices
invoices missing. She finds it humiliating and slow to SSH into production and run SQL against
`webhook_deliveries` just to learn *which* deliveries died — during an incident, at 3am.

**Solution**: Expose deliveries as a first-class, filterable read resource. This slice needs no
new data — `webhook_deliveries` already holds id, event_id, subscription_id, status, attempts,
next_attempt_at, created_at.

#### Elevator Pitch

Before: Deliveries that exhaust retries vanish; finding them requires a psql shell in production.
After: run `curl -H "X-API-Key: $KEY" "http://localhost:8080/v1/deliveries?status=failed"` → sees a cursor-paginated JSON list of every dead-lettered delivery with `id`, `event_id`, `subscription_id`, `status`, `attempts`, `created_at`.
Decision enabled: whether the incident is one consumer down or pipeline-wide, and exactly which deliveries need recovery.

#### Domain Examples

1. **Happy path** — 2026-06-14, 03:12 UTC. Priya is paged: Nimbus billing reports missing
   invoices. `curl -s -H "X-API-Key: $NIMBUS_OPS_KEY" "http://eventcore.internal:8080/v1/deliveries?status=failed&subscription_id=9f3b6c2a-1b7e-4c1d-8a4e-2f9d0c7b5a11" | jq '.deliveries | length'`
   → `137`. All created after 02:40, all for `billing-sync`. Blast radius known in one call.
2. **Edge** — During a slow-consumer incident, `?status=pending` shows 2,214 deliveries to
   `warehouse-notify` with spread-out `next_attempt_at` values — Priya distinguishes "still
   retrying, leave it" from "abandoned, act".
3. **Error** — `?status=exploded` → HTTP 400 `{"error":"status must be one of pending, delivered, failed"}`.
4. **Edge** — `?event_id=550e8400-e29b-41d4-a716-446655440000` answers "did invoice #8841's
   event reach every subscriber?" — one delivery row per subscription, statuses at a glance.

#### UAT Scenarios

```gherkin
Scenario: Operator sees every dead-lettered delivery for a struggling consumer
  Given 137 deliveries to the billing-sync subscription have exhausted their retries since 02:40
  When Priya requests GET /v1/deliveries?status=failed&subscription_id={billing-sync-id}
  Then she receives all 137, each with id, event_id, subscription_id, status "failed", attempts, and created_at
  And the response is cursor-paginated with the same contract as GET /v1/events

Scenario: Operator distinguishes retrying deliveries from dead ones
  Given 2,214 deliveries to warehouse-notify are pending with future next_attempt_at values
  When Priya requests GET /v1/deliveries?status=pending
  Then every delivery includes next_attempt_at so "still retrying" is visibly different from "abandoned"

Scenario: An invalid status filter is rejected with a typed error
  When Priya requests GET /v1/deliveries?status=exploded
  Then she receives HTTP 400 with body {"error":"status must be one of pending, delivered, failed"}

Scenario: Delivery listing is protected like the rest of the API
  When a request without an X-API-Key header hits GET /v1/deliveries
  Then it is rejected with the same typed 401 error as every other /v1 endpoint
```

#### Acceptance Criteria

- [ ] `GET /v1/deliveries` returns deliveries filterable by `status`, `subscription_id`, `event_id`, cursor-paginated consistently with `GET /v1/events`
- [ ] Each item exposes `id`, `event_id`, `subscription_id`, `status`, `attempts`, `next_attempt_at`, `created_at`
- [ ] Invalid `status` → 400 typed error; missing API key → 401 typed error
- [ ] Dogfood: against the running compose stack with a deliberately failing subscriber (returns 500), the elevator-pitch curl surfaces the dead letters it produced

**Notes**: No schema change. Read-only. Response contract as a Java record. Dependencies: none.

---

### US-02: Diagnose why a delivery failed, per attempt

`slice-02` | outcomes: **O3** (primary), O1 | effort: **1 day**

**Problem**: Today only the *count* of attempts survives. When Priya asks "why did this fail —
consumer 500? timeout? DNS?", the answer was thrown away at attempt time. The single
highest-opportunity outcome in the job (O3, 15.0) is unanswerable by design.

**Solution**: Record every delivery attempt (status code or transport error, response snippet,
duration, timestamp) and embed the history in a delivery detail endpoint.

#### Elevator Pitch

Before: Only a bare attempt count survives; the reason a delivery failed is unrecoverable.
After: run `curl -H "X-API-Key: $KEY" http://localhost:8080/v1/deliveries/7c9e6679-7425-40de-944b-e07fc1f90ae7` → sees the delivery plus a `delivery_attempts` array: per attempt `attempt`, `attempted_at`, `status_code` or `error`, `response_snippet`, `duration_ms`.
Decision enabled: whether the fix belongs to the consumer (5xx), the network (connection refused / timeout), or the producer's payload (4xx) — before touching anything.

#### Domain Examples

1. **Happy path** — Priya inspects delivery `7c9e6679-…` from the 03:12 incident: attempts 1–2
   show `status_code: 503`, `response_snippet: "upstream connect error"`; attempts 3–5 show
   `error: "connection refused"`. Diagnosis: billing-sync crashed at 02:41 right after its
   deploy. One call, root cause framed.
2. **Edge** — A delivery that failed *before* this slice deployed shows `attempts: 5` (legacy
   counter) but `"delivery_attempts": []` — history predates capture; documented, not a bug.
3. **Edge** — A timeout attempt shows `status_code: null`, `error: "timeout after 10s"`,
   `duration_ms: 10000` — distinguishing a slow consumer from a down one.
4. **Error** — `GET /v1/deliveries/00000000-0000-0000-0000-000000000000` → 404
   `{"error":"delivery not found"}`.

#### UAT Scenarios

```gherkin
Scenario: Operator reads the full attempt history of a failed delivery
  Given delivery 7c9e6679 failed after 5 attempts against a consumer that returned 503 twice then dropped connections
  When Priya requests GET /v1/deliveries/7c9e6679-7425-40de-944b-e07fc1f90ae7
  Then she sees 5 attempt records in order, each with attempted_at, status_code or error, response_snippet, and duration_ms

Scenario: Consumer error bodies are captured for diagnosis
  Given a consumer rejects a delivery with HTTP 400 and body {"error":"unknown signature"}
  When Priya inspects that delivery's attempts
  Then the attempt shows status_code 400 and a response_snippet containing "unknown signature"

Scenario: Deliveries from before attempt capture remain readable
  Given a delivery that failed before this feature was deployed
  When Priya requests its detail
  Then she sees the delivery with its legacy attempts count and an empty delivery_attempts array

Scenario: Unknown delivery id yields a typed 404
  When Priya requests GET /v1/deliveries/00000000-0000-0000-0000-000000000000
  Then she receives HTTP 404 with body {"error":"delivery not found"}
```

#### Acceptance Criteria

- [ ] Every dispatch attempt persists: attempt number, `attempted_at`, `status_code` (nullable), `error` (nullable), `response_snippet` (truncated ≤ 512 bytes), `duration_ms`
- [ ] `GET /v1/deliveries/{id}` returns the delivery with `delivery_attempts` ordered by attempt number
- [ ] Pre-existing deliveries return an empty attempt array without error
- [ ] 404 typed error for unknown ids
- [ ] Dogfood: force a local consumer to 503 twice then die; the elevator-pitch curl shows exactly that sequence

**Notes**: New attempt-record store (additive migration). Growth must be bounded — retention/cap
is flagged as a DESIGN constraint, not decided here. Dependencies: none (S1 not required, but
pairs with it in the journey).

---

### US-03: Redeliver one failed delivery

`slice-03` | outcomes: **O3**, O2 | effort: **1 day**

**Problem**: After attempt 5, a delivery is lost to that consumer forever. Priya's only
recovery today is a hand-crafted `UPDATE` in production — the exact "SSH into the database"
failure mode the DIVERGE hire-criteria names. Fixing the consumer doesn't fix the gap it left.

**Solution**: One call re-enqueues a failed delivery through the existing outbox lifecycle
(D5): status back to `pending`, `next_attempt_at` now, a fresh retry cycle, attempts keep
appending to the same history.

#### Elevator Pitch

Before: A dead-lettered delivery is unrecoverable except by hand-editing the production database.
After: run `curl -X POST -H "X-API-Key: $KEY" http://localhost:8080/v1/deliveries/7c9e6679-7425-40de-944b-e07fc1f90ae7/redeliver` → sees 202 `{"id":"7c9e6679-…","status":"pending","next_attempt_at":"<now>"}`, and seconds later the delivery reads `delivered` with attempt 6 showing `status_code: 200`.
Decision enabled: prove the downstream fix actually worked with one canary redelivery before committing to bulk recovery.

#### Domain Examples

1. **Happy path** — billing-sync redeployed at 03:30. Priya redelivers `7c9e6679-…` as a
   canary; 10 seconds later `GET /v1/deliveries/7c9e6679-…` shows `status: "delivered"`,
   attempt 6 `status_code: 200`. Fix confirmed; safe to bulk-recover the other 136.
2. **Edge** — Consumer still broken: the redelivered delivery runs a fresh 5-attempt cycle
   (attempts 6–10 recorded) and returns to `failed`. Nothing loops forever; the history tells
   the whole story.
3. **Error** — Redelivering an already-`delivered` delivery → 409
   `{"error":"only failed deliveries can be redelivered"}` (no accidental duplicates to healthy consumers).
4. **Error** — Unknown id → 404 `{"error":"delivery not found"}`.

#### UAT Scenarios

```gherkin
Scenario: Operator recovers a dead-lettered delivery after fixing the consumer
  Given delivery 7c9e6679 is failed and its consumer has been fixed
  When Priya posts to /v1/deliveries/7c9e6679-7425-40de-944b-e07fc1f90ae7/redeliver
  Then she receives 202 with the delivery now pending and next_attempt_at set to now
  And within the dispatcher interval the delivery becomes delivered with attempt 6 recording status_code 200

Scenario: A redelivered delivery that fails again dead-letters normally
  Given delivery 7c9e6679 is failed and its consumer is still down
  When Priya redelivers it and the consumer keeps refusing connections
  Then the delivery runs a fresh retry cycle, appends attempts 6-10 to its history, and returns to failed

Scenario: Delivered deliveries cannot be redelivered
  Given a delivery with status delivered
  When Priya posts to its /redeliver endpoint
  Then she receives HTTP 409 with body {"error":"only failed deliveries can be redelivered"}

Scenario: Redelivering an unknown delivery yields a typed 404
  When Priya posts to /v1/deliveries/00000000-0000-0000-0000-000000000000/redeliver
  Then she receives HTTP 404 with body {"error":"delivery not found"}
```

#### Acceptance Criteria

- [ ] `POST /v1/deliveries/{id}/redeliver` on a `failed` delivery → 202, status `pending`, `next_attempt_at` ≈ now; dispatcher picks it up with normal retry/HMAC semantics
- [ ] Attempt history is cumulative across redeliveries (numbers continue, never reset)
- [ ] Non-`failed` delivery → 409 typed error; unknown id → 404 typed error
- [ ] Dogfood: kill the local consumer, dead-letter a delivery, fix the consumer, redeliver, watch it turn `delivered` — all via curl against compose

**Notes**: Race-safety with an in-flight dispatcher pass is a DESIGN concern (mitigated by
failed-only eligibility). Dependencies: none hard; journey-pairs with S1/S2.

---

### US-04: Bulk-redeliver an outage's dead letters

`slice-04` | outcomes: **O2**, O3 | effort: **0.5 day**

**Problem**: Outages dead-letter deliveries by the hundred. After confirming a fix with one
canary (US-03), Priya will not curl 136 ids one by one at 3am — she'd write a loop script,
which is the same production-shell anti-pattern with extra steps.

**Solution**: Filter-based bulk requeue (D8): explicit `"status": "failed"` required, optional
`subscription_id`, returns the requeued count.

#### Elevator Pitch

Before: Recovering hundreds of dead letters means scripting a loop against the API or the database.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"status":"failed","subscription_id":"9f3b6c2a-1b7e-4c1d-8a4e-2f9d0c7b5a11"}' http://localhost:8080/v1/deliveries/redeliver` → sees 202 `{"requeued":136}`.
Decision enabled: declare the incident recovered when the requeued count matches the dead-letter count and `?status=failed` drains to zero.

#### Domain Examples

1. **Happy path** — Canary confirmed at 03:32. Priya bulk-requeues billing-sync's remaining 136
   failures; response `{"requeued":136}`. Two minutes later `?status=failed&subscription_id=9f3b…`
   returns an empty list. Incident closed at 03:35 — 23 minutes end to end.
2. **Edge** — Re-running the same call → `{"requeued":0}` with 202. Idempotent and safe; no
   double-queuing of already-pending deliveries.
3. **Edge** — After a datacenter-wide network blip dead-letters deliveries across all four
   Nimbus subscriptions, omitting `subscription_id` requeues every failed delivery instance-wide.
4. **Error** — Body without `status` → 400 `{"error":"status is required and must be \"failed\""}`.

#### UAT Scenarios

```gherkin
Scenario: Operator recovers all of a subscription's dead letters in one call
  Given 136 failed deliveries remain for the billing-sync subscription
  When Priya posts {"status":"failed","subscription_id":"9f3b6c2a-…"} to /v1/deliveries/redeliver
  Then she receives 202 with {"requeued":136}
  And a subsequent GET /v1/deliveries?status=failed&subscription_id=9f3b6c2a-… returns no deliveries once the dispatcher drains

Scenario: Bulk redelivery is idempotent when nothing matches
  Given no failed deliveries match the filter
  When Priya posts the same bulk redeliver request again
  Then she receives 202 with {"requeued":0}

Scenario: Bulk redelivery refuses an implicit selection
  When Priya posts a body without the status field to /v1/deliveries/redeliver
  Then she receives HTTP 400 with body {"error":"status is required and must be \"failed\""}
```

#### Acceptance Criteria

- [ ] `POST /v1/deliveries/redeliver` with `{"status":"failed"[, "subscription_id"]}` requeues all matching failed deliveries (same lifecycle as US-03) and returns 202 `{"requeued": N}`
- [ ] Zero matches → `{"requeued": 0}`, still 202 (idempotent)
- [ ] Missing/other `status` → 400 typed error
- [ ] Dogfood: dead-letter ≥3 deliveries on compose, bulk-recover, verify the failed list drains

**Notes**: Depends on US-03 (redelivery lifecycle). Selection cap / batching is a DESIGN
concern if counts get large.

---

### US-05: Read pipeline health from one metrics endpoint

`slice-05` | outcomes: **O5**, O3 | effort: **1 day**

**Problem**: EventCore's only signal today is `/health` liveness. Queue depth, failure counts,
and a stalled dispatcher are invisible; Priya learns about pipeline trouble from the very
downstream teams the pipeline was supposed to keep consistent — the trust product inverted.

**Solution**: `GET /metrics` in Prometheus text format (D4), derived from tables that already
exist: delivery counts by status, oldest pending age, ingested-event and attempt counters.

#### Elevator Pitch

Before: The only health signal is `/health` liveness; backlog, failures, and dispatcher stalls are invisible until humans report gaps.
After: run `curl http://localhost:8080/metrics` → sees Prometheus text including `eventcore_deliveries{status="failed"} 137`, `eventcore_deliveries{status="pending"} 12`, `eventcore_oldest_pending_delivery_age_seconds 42`, `eventcore_events_ingested_total 128412`, `eventcore_delivery_attempts_total{result="failure"} 685`.
Decision enabled: page (or don't) on real pipeline state — a failed spike, a growing backlog, a stalled dispatcher — before any dependent notices.

#### Domain Examples

1. **Happy path** — Priya adds a scrape job for `eventcore.internal:8080/metrics`. Her Grafana
   panel shows `eventcore_deliveries{status="failed"}` jump 0 → 137 at 02:41 on 2026-06-14. Next
   incident, the page fires from the graph — not from the billing team.
2. **Edge** — A freshly booted instance exposes every series with value `0` (no absent-metric
   holes that break alert rules).
3. **Edge** — `eventcore_oldest_pending_delivery_age_seconds` climbing while `pending` count is
   flat exposes a stalled dispatcher — a failure mode nothing else can reveal.
4. **Boundary** — `/metrics` needs no `X-API-Key` (scrapers hold no keys), while `/v1/**`
   endpoints still reject keyless requests — no auth regression.

#### UAT Scenarios

```gherkin
Scenario: Operator scrapes pipeline state in Prometheus format
  Given the pipeline has 137 failed, 12 pending, and 4,891 delivered deliveries
  When Priya requests GET /metrics
  Then she receives Prometheus text exposition with eventcore_deliveries gauges per status matching those counts
  And counters for events ingested and delivery attempts by result

Scenario: A fresh instance exposes zero-valued series rather than missing ones
  Given a newly started EventCore with no traffic
  When Priya requests GET /metrics
  Then all documented eventcore_ series are present with value 0

Scenario: A stalled dispatcher becomes visible through pending age
  Given deliveries are pending and the dispatcher has not run for 5 minutes
  When Priya requests GET /metrics
  Then eventcore_oldest_pending_delivery_age_seconds reports at least 300

Scenario: Metrics are scrapeable without an API key while the API stays protected
  When a request without X-API-Key hits GET /metrics and another hits GET /v1/events
  Then /metrics returns 200 and /v1/events returns the standard typed 401
```

#### Acceptance Criteria

- [ ] `GET /metrics` serves Prometheus text format, unauthenticated (D4)
- [ ] Gauges: `eventcore_deliveries{status=…}` (all three statuses always present), `eventcore_oldest_pending_delivery_age_seconds`
- [ ] Counters: `eventcore_events_ingested_total`, `eventcore_delivery_attempts_total{result="success"|"failure"}` (may reset on restart — Prometheus `rate()` convention; gauges must reflect database truth)
- [ ] Dogfood: scrape the compose instance during a forced failure burst; the failed gauge tracks `GET /v1/deliveries?status=failed` counts

**Notes**: Independent of S1–S4 (derives from existing columns). Network exposure guidance →
DESIGN/DEVOPS. Dependencies: none.

---

### US-06: Detect that a producer's event flow stopped

`slice-06` | outcomes: **O5** (primary) | effort: **0.5 day**

**Problem**: When a producer silently stops publishing (a deploy mutes checkout's
`order.placed` events), nothing fails — events simply stop existing. Nimbus discovered one such
silence a full day later, via a bewildered warehouse team. Absence has no error path.

**Solution (thin slice per D3)**: expose per-event-type last-received timestamps on `/metrics`
plus a documented, copy-pasteable alert rule. Detection runs in the operator's existing
Prometheus stack; EventCore supplies the signal, not the alerting engine.

#### Elevator Pitch

Before: When a producer silently stops publishing, nobody knows until a downstream team asks where the data went — hours or days later.
After: run `curl -s http://localhost:8080/metrics | grep last_received` → sees `eventcore_event_last_received_timestamp_seconds{type="order.placed"} 1.7657424e+09` per event type, and the documented alert rule `time() - eventcore_event_last_received_timestamp_seconds{type="order.placed"} > 900` fires after 15 minutes of silence.
Decision enabled: treat producer silence as an incident within minutes — roll back the deploy that muted checkout instead of discovering it tomorrow.

#### Domain Examples

1. **Happy path** — `order.placed` normally arrives every ~1 minute. A checkout deploy at 14:05
   silently stops publishing. Priya's alert rule fires at 14:20; checkout is rolled back by
   14:31. Previous comparable incident: discovered next day.
2. **Edge** — A type EventCore has never seen has no series; the runbook documents the
   `absent()` pattern for types that must always exist.
3. **Boundary** — Nimbus emits 14 event types → 14 series. Cardinality guidance: intended for
   first-party type counts (≤ ~100), noted in docs.

#### UAT Scenarios

```gherkin
Scenario: Silence in a normally chatty event type is detectable within one alert window
  Given order.placed events have been ingested regularly and the last arrived at 14:05
  When Priya scrapes GET /metrics at 14:21
  Then eventcore_event_last_received_timestamp_seconds{type="order.placed"} still reports the 14:05 timestamp
  And the documented rule time() - that_series > 900 evaluates true

Scenario: Every ingested event type gets a freshness series
  Given events of types order.placed, invoice.paid, and refund.issued have each been ingested
  When Priya requests GET /metrics
  Then a last_received timestamp series exists for each of the three types

Scenario: The alert recipe ships with the feature
  When Priya reads the metrics documentation
  Then it contains a copy-pasteable Prometheus alert rule for flow-stopped detection with a 15-minute default window
```

#### Acceptance Criteria

- [ ] `eventcore_event_last_received_timestamp_seconds{type=…}` exposed for every event type seen since startup (timestamp, not age — PromQL computes age)
- [ ] Values update on ingest and survive scrape-to-scrape without drift
- [ ] Documentation includes the alert rule and the `absent()` recipe for never-seen types
- [ ] Dogfood: seed a type on compose, stop seeding, watch the documented rule's expression go true after the window

**Notes**: Depends on US-05 (`/metrics` exists). Built-in monitor resources remain out of scope
(D3) — re-open only if this slice's hypothesis fails.

---

### US-07: Subscribe a consumer to only the event types it wants

`slice-07` | outcomes: **O6** (primary) | effort: **1 day**

**Problem**: Every subscription receives every event. Nimbus's `warehouse-notify` needs
`order.placed` and `refund.issued` but is force-fed all 14 types — including
`user.signed_up`, whose payload carries customer data the warehouse vendor has no business
seeing. Consumers burn code discarding noise, and data crosses boundaries it shouldn't.

**Solution**: Optional `event_types` list on subscription creation (D7). Matching is exact;
omitted means all events — every existing subscription keeps its behavior untouched.

#### Elevator Pitch

Before: Every subscription receives every event; consumers must discard noise, and sensitive types cross boundaries they shouldn't.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"url":"https://warehouse.nimbus-partners.io/hooks/eventcore","event_types":["order.placed","refund.issued"]}' http://localhost:8080/v1/webhooks` → sees the created subscription echoing `"event_types":["order.placed","refund.issued"]`, and only matching deliveries ever appear for it in GET /v1/deliveries.
Decision enabled: which systems see which event types — subscription scope becomes a deliberate choice instead of all-or-nothing.

#### Domain Examples

1. **Happy path** — Priya recreates warehouse-notify with
   `event_types: ["order.placed","refund.issued"]`. Over the next hour EventCore ingests 412
   events across 14 types; warehouse-notify receives exactly the 87 matching ones.
   `user.signed_up` never leaves the boundary. Unwanted deliveries: 0 (KPI-4).
2. **Edge** — Subscriptions created without `event_types` (including all four pre-existing
   Nimbus subscriptions) continue receiving everything — zero behavior change on upgrade.
3. **Edge** — Filtering on `refund.issued` before any such event has ever occurred is accepted
   — types are free-form strings, no registry to validate against.
4. **Error** — `"event_types": []` → 400
   `{"error":"event_types must be null or a non-empty array"}` ("subscribe to nothing" is a
   contradiction we refuse, per D7).

#### UAT Scenarios

```gherkin
Scenario: A filtered subscription receives only its chosen types
  Given a subscription created with event_types ["order.placed","refund.issued"]
  When events of types order.placed, user.signed_up, and invoice.paid are ingested
  Then deliveries are created for the order.placed event only among those three
  And GET /v1/deliveries?subscription_id={id} never shows a non-matching event type

Scenario: Existing unfiltered subscriptions are untouched
  Given a subscription created before filters existed, with no event_types
  When any event is ingested
  Then that subscription still receives a delivery for it

Scenario: Subscriptions list shows each filter
  Given filtered and unfiltered subscriptions exist
  When Priya requests GET /v1/webhooks
  Then filtered subscriptions show their event_types and unfiltered ones show null

Scenario: An empty filter list is rejected
  When Priya creates a subscription with "event_types": []
  Then she receives HTTP 400 with body {"error":"event_types must be null or a non-empty array"}
```

#### Acceptance Criteria

- [ ] `POST /v1/webhooks` accepts optional `event_types` (non-empty string array); response and `GET /v1/webhooks` echo it (`null` = unfiltered)
- [ ] Dispatcher creates deliveries only for matching types on filtered subscriptions; exact string match
- [ ] Omitted/`null` = all events; `[]` = 400 typed error; existing rows unaffected (additive migration)
- [ ] Dogfood: on compose, a filtered and an unfiltered subscription side by side receive 1 and 3 deliveries respectively from a 3-type seed batch

**Notes**: Additive column. Filter evaluation at delivery-creation (outbox insert) time, not
dispatch time — implies D7's "future events only" semantics. Dependencies: none.

---

### US-08: Change a subscription's filter without rotating its secret

`slice-08` | outcomes: **O6** | effort: **0.5 day**

**Problem**: With create-time filters only, changing what warehouse-notify receives means
DELETE + re-POST — a new subscription id and a new HMAC secret, forcing the warehouse vendor to
redeploy config in lockstep. A one-line change becomes a cross-team ceremony.

**Solution**: `PATCH /v1/webhooks/{id}` updates `event_types` in place; id and secret survive.

#### Elevator Pitch

Before: Changing a subscription's filter means delete-and-recreate — new id, new secret, and a forced secret-rotation ceremony with the consumer's team.
After: run `curl -X PATCH -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"event_types":["order.placed","refund.issued","return.received"]}' http://localhost:8080/v1/webhooks/4d2a8c1e-6f3b-4e9a-b7d5-0c8e2a4f6b91` → sees the subscription with the same id, updated event_types, and its HMAC signatures still verifying downstream.
Decision enabled: evolve a consumer's event diet in one call, with zero coordination cost.

#### Domain Examples

1. **Happy path** — Nimbus launches returns handling. Priya PATCHes warehouse-notify to add
   `return.received`. The vendor's HMAC verification keeps passing (secret unchanged); the next
   `return.received` event is delivered. Elapsed cross-team coordination: none.
2. **Edge** — `{"event_types": null}` clears the filter — the subscription goes back to
   receiving everything (explicit, documented inverse of D7).
3. **Edge** — The filter change affects future events only: events ingested before the PATCH
   are not retroactively delivered (that would be replay — out of scope, O8).
4. **Error** — PATCH on an unknown id → 404 typed error; `"event_types": []` → 400 typed error
   (same rule as creation).

#### UAT Scenarios

```gherkin
Scenario: Operator widens a filter in place
  Given the warehouse-notify subscription is filtered to ["order.placed","refund.issued"]
  When Priya PATCHes it with event_types ["order.placed","refund.issued","return.received"]
  Then the response shows the same subscription id with the updated filter
  And a subsequently ingested return.received event is delivered with an HMAC signature the existing secret verifies

Scenario: Filter changes are not retroactive
  Given a return.received event was ingested before the PATCH
  When the filter is widened to include return.received
  Then no delivery is created for the pre-existing event

Scenario: Clearing a filter restores receive-everything
  Given a filtered subscription
  When Priya PATCHes it with {"event_types": null}
  Then the subscription subsequently receives deliveries for all event types

Scenario: Invalid PATCH targets and bodies get typed errors
  When Priya PATCHes an unknown subscription id, and separately PATCHes a real one with "event_types": []
  Then she receives HTTP 404 {"error":"subscription not found"} and HTTP 400 {"error":"event_types must be null or a non-empty array"} respectively
```

#### Acceptance Criteria

- [ ] `PATCH /v1/webhooks/{id}` replaces `event_types`; `null` clears; `[]` → 400; unknown id → 404
- [ ] Subscription `id` and `secret` are unchanged by PATCH (verified end-to-end by signature validation on the next delivery)
- [ ] Changes apply to events ingested after the PATCH only
- [ ] Dogfood: on compose, widen a filter and watch the next matching event arrive, signature-valid

**Notes**: Depends on US-07. Only `event_types` is PATCHable in this slice (URL changes out of
scope).

---

## Wave: DISCUSS / [REF] Acceptance Criteria

Embedded per story above (no standalone file). Every AC set includes one end-to-end check that
the story's Elevator Pitch "After" command produces the "sees" output against the running
compose deployment — that is the per-slice dogfood gate.

## Wave: DISCUSS / [REF] Outcome KPIs

| KPI | Outcome | Who / does what / by how much | Baseline | Numeric target | Measured by |
|---|---|---|---|---|---|
| KPI-1 | O3 | On-call operator determines why a delivery failed | 30–60+ min of SSH + psql archaeology; often inconclusive (attempt data discarded) | **≤ 5 minutes, ≤ 2 API calls** | Incident timeline: page timestamp → cause-identified note; API-call count in the diagnosis path |
| KPI-2 | O3, O2 | Operator recovers failed deliveries without database access | 0% recoverable via API (manual `UPDATE` only) | **100% of failed deliveries recoverable via API; median list→redeliver ≤ 2 min** | `failed→delivered` transitions initiated via redeliver endpoints vs total recoveries; incident reviews |
| KPI-3 | O5 | Operator detects a stopped producer flow | Hours to days (human/customer report) | **≤ 15 minutes from last expected event** (documented alert window) | Alert firing timestamp minus last-event timestamp for the type |
| KPI-4 | O6 | Filtered subscription receives only requested types | 100% of non-matching events delivered (all subscriptions get everything) | **0 non-matching deliveries** for filtered subscriptions | Deliveries joined to event type vs subscription filter; assert zero mismatches |
| KPI-5 | O3, O5 | Database shell sessions needed during a delivery incident | ≥ 1 per incident | **0** | Post-incident review checklist item |

## Wave: DISCUSS / [REF] Definition of Ready — 9-Item Validation

Method: 8 stories × 9 items = 72 checks. **Passed: 72/72. Requirements completeness score:
1.00 (> 0.95 gate).**

| # | DoR item | Verdict | Evidence |
|---|---|---|---|
| 1 | Problem statement clear, domain language | PASS | Every story opens with a Problem grounded in operator pain (dead letters, silence, noise) — no implement-X framing |
| 2 | User/persona with specific characteristics | PASS | `platform-operator` (SSOT yaml) + exemplar Priya Raghavan, Nimbus Retail, used consistently in all 8 stories |
| 3 | 3+ domain examples with real data | PASS | 3–4 examples per story with real names, timestamps (2026-06-14 02:41), UUIDs, counts (137/136/2,214), URLs |
| 4 | UAT in Given/When/Then, 3–7 scenarios | PASS | Counts: US-01:4, US-02:4, US-03:4, US-04:3, US-05:4, US-06:3, US-07:4, US-08:4 |
| 5 | AC derived from UAT | PASS | Each AC checklist maps to its story's scenarios plus the elevator-pitch end-to-end check |
| 6 | Right-sized (1–3 days, 3–7 scenarios) | PASS | All stories 0.5–1 day (carpaccio ceiling), scenario counts in range |
| 7 | Technical notes: constraints/dependencies | PASS | Per-story Notes; cross-cutting constraints in Wave Decisions Summary |
| 8 | Dependencies resolved or tracked | PASS | Only intra-feature: US-04→US-03, US-06→US-05, US-08→US-07; encoded in slice order |
| 9 | Outcome KPIs with measurable targets | PASS | KPI-1–5, all numeric, each with baseline + measurement method |

## Wave: DISCUSS / [REF] Out-of-Scope

1. **Built-in flow-monitor resources** (monitor CRUD, expected-flow definitions, notification
   channels) — thin-sliced to metrics + alert rule (D3). Rationale: persona already operates a
   Prometheus stack; duplicating an alerting engine adds surface without adding signal. Re-open
   if slice-06's hypothesis fails.
2. **Replay/redelivery of already-delivered events; consumer backfill (O8)** — Durable Pull
   Subscriptions territory (DIVERGE dissent). Note: attempt records + redelivery mechanics
   built here are deliberately compatible substrate for it.
3. **Payload/time-range/correlation search (O1 in full)** — Forensic Search direction; only the
   `event_id`/`subscription_id` delivery filters land here.
4. **Retention automation (O7)** and **tamper-evidence (O4)** — lower-priority outcomes.
5. **Web dashboard / any UI** — API-first per feature type; the terminal is the interface.
6. **Wildcard/prefix filter patterns** — exact match only until slice-07 evidence demands more.
7. **Subscription pause/disable and URL mutation via PATCH** — only `event_types` is mutable.

## Wave: DISCUSS / [REF] WS Strategy

Not applicable — brownfield. The ingest→store→deliver spine already runs end-to-end (44 green
integration tests, compose deployment); every slice extends a working system. The feature-level
equivalent is the recovery spine S1→S2→S3 (see Story Map), which connects the new journey
end-to-end by slice 3.

## Wave: DISCUSS / [REF] Driving Ports

HTTP API only (no CLI, no UI):

| Surface | Auth | New/changed |
|---|---|---|
| `GET /v1/deliveries` (filters: status, subscription_id, event_id; cursor) | X-API-Key | new |
| `GET /v1/deliveries/{id}` (embeds `delivery_attempts`) | X-API-Key | new |
| `POST /v1/deliveries/{id}/redeliver` | X-API-Key | new |
| `POST /v1/deliveries/redeliver` (bulk, filter body) | X-API-Key | new |
| `POST /v1/webhooks` (+ optional `event_types`) | X-API-Key | extended |
| `PATCH /v1/webhooks/{id}` (`event_types` only) | X-API-Key | new |
| `GET /metrics` (Prometheus text) | none (like `/health`) | new |

## Wave: DISCUSS / [REF] Pre-requisites

- DIVERGE artifacts complete and peer-approved (`docs/feature/eventcore/diverge/`,
  `recommendation.md`) — satisfied.
- `docs/product/jobs.yaml` with JOB-001 — satisfied.
- Running compose deployment for per-slice dogfood — satisfied (repo state).
- **Known risk carried forward**: no DISCOVER wave ran; JOB-001 opportunity scores are
  desk-research proxies. This feature's slice hypotheses (especially slice-01 and slice-05/06)
  double as cheap validation probes; if dogfood signals contradict the proxies, re-open per the
  recommendation's re-open clause.

## Wave: DISCUSS / [REF] Wave Decisions Summary

### Key decisions

- [D1–D10] See Locked Decisions above.

### Requirements summary

- Primary need: the operator of a first-party EventCore instance must see, explain, and fix
  delivery failures — and right-size what each dependent receives — without a database shell.
  8 stories across 3 outcome-named releases; 6 dev-days total; every slice ≤1 day and curl-able.
- Feature type: **backend** (API-first, no UI).
- Walking skeleton: N/A (brownfield); recovery spine S1→S3 plays the role for the new journey.

### Constraints established (for DESIGN)

- All new `/v1` endpoints follow existing conventions: `X-API-Key`, typed errors
  `{"error": "..."}`, Java records for contracts, cursor pagination matching `GET /v1/events`.
- Zero breaking changes for existing deployments: migrations additive; unfiltered subscriptions
  keep receiving everything; legacy deliveries readable with empty attempt arrays.
- Attempt-record storage growth must be bounded (retention/cap) — DESIGN decision.
- `/metrics` is unauthenticated → deployment/network-exposure guidance needed (DESIGN/DEVOPS).
- Metric label cardinality: per-type series assume first-party type counts (≤ ~100).
- Redelivery vs in-flight dispatcher race-safety — DESIGN must validate (failed-only
  eligibility is the requirements-level mitigation).
- TDD with Testcontainers, prose-named tests (existing repo convention) applies to all slices.

### Upstream changes

- None. No DISCOVER artifacts exist to contradict; DIVERGE assumption (a) was affirmed (D1),
  not changed. jobs.yaml untouched except a DISCUSS changelog entry.

### Process notes

- Telemetry: skipped (D9) — helper scripts absent from this repo.
- Peer review: skipped (D10) — no trigger; consolidated review at end of DISTILL.

---

## Wave: DISCUSS / [WHY] JTBD Narrative

Bridge only — JOB-001 was extracted and desk-validated in DIVERGE; not re-run here.

**Dimensions** (from `diverge/job-analysis.md`): functional — record every consequential action
durably and convey it to all dependents; emotional — feel calm during an incident or audit,
certain nothing was silently dropped; social — be seen as an operation whose word about its own
behavior can be trusted. This feature attacks the *emotional* dimension most directly: today's
system is calm-destroying precisely at attempt 6, when it goes silent.

**Four forces** (derived from DIVERGE evidence, not customer interviews — same proxy caveat):

- **Push**: post-deployment blindness — deliveries dead-letter silently after attempt 5; no
  attempt record survives; diagnosis means production psql at 3am.
- **Pull**: see, explain, and fix any failure with two curl calls; declare incidents recovered
  with numbers, not hope.
- **Anxiety**: does redelivery double-send? does the new surface leak data (`/metrics` auth,
  filter boundaries)? — addressed in D5 (failed-only), D4 (exposure guidance), US-07 (boundary
  examples).
- **Habit**: SSH + psql spelunking and hand-rolled recovery scripts — the workflow every story's
  Before line names and every KPI-5 measurement retires.

**Story-to-outcome map** (N:1 onto JOB-001):

| Outcome | Opportunity | Stories |
|---|---|---|
| O3 diagnose delivery failure | 15.0 | US-01, US-02, US-03 (US-04 secondary) |
| O5 detect flow stopped | 12.5 | US-05, US-06 |
| O6 unwanted events | 11.0 | US-07, US-08 |
| O1 locate incident events | 14.0 | secondary via US-01 `event_id` filter, US-02 attempt forensics |
| O2 never miss an event | 13.0 | secondary via US-03/US-04 (the attempt-6 silent-loss tail) |

## Wave: DISCUSS / [WHY] Persona Narrative

**Priya Raghavan** (exemplar of `platform-operator`) — platform engineer at Nimbus Retail, a
mid-size e-commerce company running EventCore on compose for its order pipeline (14 event
types, 4 webhook subscriptions: billing-sync, crm-sync, warehouse-notify, fraud-screen).
On-call one week in four. Terminal-native: curl, jq, psql, Grafana; automates anything she does
twice. **Goals**: integrations stay consistent without her babysitting them; incidents end with
evidence, not shrugs. **Frustrations**: EventCore is a black box after deployment — "the
pipeline works until the night it doesn't, and that night it has nothing to say for itself."
**Mental model**: the outbox is a queue she can trust *until attempt 6*; she thinks in terms of
"which consumer, which event types, since when". **Vocabulary**: dead letter, requeue,
backlog, canary, blast radius, scrape, silence window. **Trust rule**: she believes API
responses she can cross-check against the database once — after that she stops opening psql.
SSOT: `docs/product/personas/platform-operator.yaml`.

## Wave: DISCUSS / [WHY] Alternatives Considered

- **D3 (flow monitors)** — Alternative: built-in monitor resources (`POST /v1/monitors` with
  expected-cadence config + notifier). Rejected: duplicates the persona's Prometheus alerting
  engine, adds CRUD+scheduler+notification surface (3+ components — fails carpaccio taste
  test), and delays O5 by days for the same detection latency. Chosen thin slice delivers the
  unique part (the signal only EventCore has) and delegates the commodity part (alert routing).
- **D4 (metrics format)** — Alternative: JSON health document. Rejected: persona tooling speaks
  Prometheus; JSON would require a custom exporter to become alertable — recreating the gap.
- **D5 (redelivery path)** — Alternative: synchronous bypass send returning the consumer's
  response inline. Rejected: second delivery path means second HMAC/retry/attempt-recording
  code path and race conditions; outbox re-enqueue reuses proven semantics. Cost: recovery
  confirmation is eventually-consistent (poll the detail endpoint) — acceptable, examples show
  the pattern.
- **D7 (filter semantics)** — Alternative: wildcard/prefix patterns (`order.*`). Rejected for
  now: exact match covers the persona's 14-type reality; pattern semantics (escaping,
  precedence) are a rabbit hole with no evidenced demand. Revisit on slice-07 feedback.
- **D8 (bulk selection)** — Alternative: id-list body. Rejected: outage recovery is
  filter-shaped ("everything failed for billing-sync"), and id lists invite partial-failure
  ambiguity. Hypothesis recorded in slice-04; id-list returns if it fails.

## Wave: DISCUSS / [HOW] Migration Playbook

For teams already running EventCore (compose upgrade):

1. **Schema**: migrations are additive only — new attempt-record store (US-02), new nullable
   `event_types` on `webhook_subscriptions` (US-07). No existing column changes; no data
   backfill required.
2. **Behavioral compatibility**: existing subscriptions have `event_types = null` → continue
   receiving every event (D7). Deliveries dead-lettered before upgrade list with their legacy
   `attempts` count and an empty `delivery_attempts` array (US-02 edge) — history begins at
   upgrade.
3. **New unauthenticated surface**: `GET /metrics` joins `/health` outside the API-key realm.
   If your instance is internet-exposed, restrict `/metrics` at the reverse proxy before
   upgrading (DESIGN will ship concrete guidance).
4. **Recoverability starts now**: failed deliveries created before the upgrade ARE eligible for
   `POST /v1/deliveries/{id}/redeliver` — the outbox rows are all that's needed. First dogfood
   act after upgrade: list your historical dead letters and decide which still matter.
5. **No API client changes required**: all existing endpoints behave identically.

## Wave: DISCUSS / [HOW] Journey Deep-Dive

Journey: **operator-recovers-failed-delivery** (SSOT:
`docs/product/journeys/operator-recovers-failed-delivery.yaml`). Trigger: paged at 03:12 —
downstream team reports missing data (post-S5/S6: a Grafana alert fires instead).

### Emotional arc

| Step | Action | Surface | Emotion |
|---|---|---|---|
| 1. Alerted | Page arrives, symptom vague ("invoices missing") | pager | startled, anxious |
| 2. Locate | List failed deliveries, scope blast radius | `GET /v1/deliveries?status=failed…` | oriented — "it's one consumer, 137 deliveries, since 02:40" |
| 3. Diagnose | Read attempt history: 503s then connection refused | `GET /v1/deliveries/{id}` | informed — cause named, fix owner known |
| 4. Fix downstream | Roll back / redeploy billing-sync | outside EventCore | focused (external step, tracked not owned) |
| 5. Recover | Canary redeliver, then bulk requeue | `POST …/redeliver`, bulk | in control — recovery is a verb now |
| 6. Verify | Failed list drains; failed gauge → 0 | `GET /v1/deliveries?status=failed`, `/metrics` | calm — closed loop with evidence |

The arc lands exactly on JOB-001's emotional dimension ("feel calm during an incident —
certain nothing was silently dropped"). Confidence builds monotonically from step 2 onward; the
only dip risk is step 4 (external), bounded by the canary pattern in step 5.

### Shared artifacts registry

| Artifact | Single source of truth | Consumed by |
|---|---|---|
| `${api_key}` | issued via `POST /v1/api-keys` (admin) | every `/v1` call in steps 2–6 |
| `${subscription_id}` | `GET /v1/webhooks` | steps 2 (filter), 5 (bulk body), US-07/08 |
| `${delivery_id}` | step-2 list response (`deliveries[].id`) | steps 3, 5 (canary) |
| `${event_id}` | step-3 detail response | cross-check against `GET /v1/events?type=` |
| failed count (137) | step-2 list | step-6 verification (must reach 0) and `{"requeued":N}` reconciliation |

Every `${variable}` in the examples has exactly one producing endpoint — no operator-invented
identifiers.

### Error-path map (risky steps)

| Step | Failure mode | Recovery |
|---|---|---|
| 2 Locate | Wrong/unknown status filter | 400 typed error names valid values (US-01) |
| 3 Diagnose | Pre-upgrade delivery, no attempt rows | Empty array + legacy count; documented (US-02) |
| 5 Recover | Redeliver non-failed delivery | 409 typed error — protects healthy consumers (US-03) |
| 5 Recover | Consumer still broken after redelivery | Fresh retry cycle → back to failed with attempts 6–10 as evidence (US-03) |
| 5 Recover | Bulk matches nothing / re-run | `{"requeued":0}`, idempotent (US-04) |
| 6 Verify | Failed count not draining | Pending gauge + oldest-pending-age expose a stalled dispatcher (US-05) |

## Wave: DISCUSS / [HOW] Gherkin Scenarios

Journey-level scenarios (story-level scenarios live in each story above; both are embedded —
no standalone `.feature` file per lean layout):

```gherkin
Feature: Operator recovers a failed webhook delivery without database access

  Scenario: The 3am incident ends in 23 minutes with evidence
    Given billing-sync crashed at 02:41 and 137 deliveries to it dead-lettered
    And Priya is paged at 03:12 with "invoices missing"
    When she lists failed deliveries filtered to billing-sync
    And inspects one delivery's attempt history showing 503s then connection refused
    And the billing team redeploys billing-sync
    And she redelivers one canary delivery and sees it become delivered
    And she bulk-redelivers with status failed and the billing-sync subscription id
    Then the response reports 136 requeued
    And the failed delivery list for billing-sync drains to zero
    And at no point did anyone open a database shell

  Scenario: Producer silence is caught by the pipeline, not by customers
    Given order.placed events normally arrive every minute
    When a checkout deploy silently stops publishing at 14:05
    Then the documented alert rule fires within 15 minutes
    And Priya rolls back checkout before any downstream team notices

  Scenario: A partner sees only what it was granted
    Given warehouse-notify is filtered to order.placed and refund.issued
    When a user.signed_up event is ingested
    Then no delivery to warehouse-notify is created for it
```

## Wave: DISCUSS / [WHY] Reviewer Findings Trace

Not applicable this wave: per-wave peer review skipped (D10, no trigger). The R-chain will be
produced by the consolidated Eclipse+Architect+Forge+Sentinel review at end of DISTILL against
this file.

## Wave: DISCUSS / [WHY] Expansion Catalog Rationale

mode=full renders all applicable Tier-2 items: JTBD narrative and persona narrative carry the
WHY that Tier-1 compresses; alternatives-considered preserves decision reversibility;
migration-playbook exists because this is a brownfield upgrade with a new unauthenticated
surface; journey deep-dive and Gherkin carry the HOW that DISTILL will consume directly.
Reviewer-findings-trace is stubbed (no review ran). No expansion menu was shown —
`expansion_prompt` is irrelevant in full mode by contract.
