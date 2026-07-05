<!-- markdownlint-disable MD024 -->
# Feature Delta — pull-subscriptions (Durable Pull Subscriptions & Replay)

**Wave**: DISCUSS | **Agent**: Luna (nw-product-owner) | **Date**: 2026-07-05
**Upstream**: DIVERGE dissenting option (`docs/feature/eventcore/recommendation.md`, "Durable
Pull Subscriptions & Replay", score 3.80) converted post-parity — see D1 and Changed
Assumptions. Job SSOT: `docs/product/jobs.yaml` (JOB-001, desk-validated; primary outcome O8).
**Density**: mode=full (Tier-1 [REF] + all applicable Tier-2 expansions rendered).

---

## Wave: DISCUSS / [REF] Persona ID

`platform-operator` — first-party platform/backend engineer, terminal-native, curl/jq fluent
(SSOT: `docs/product/personas/platform-operator.yaml`, unchanged). Two exemplar instances of
the same persona class appear in this feature:

- **Tomás Ferreira** — backend engineer on Nimbus Retail's new fraud-analytics team, building
  `fraud-lens`, a consumer that did not exist when most of the events it needs were recorded.
  He drives the consumer-side stories (US-01, US-02, US-03, US-05).
- **Priya Raghavan** — the established EventCore operator exemplar; she drives the fleet
  oversight story (US-04).

## Wave: DISCUSS / [REF] JTBD One-liner

**JOB-001** (strategic, desk-validated — bridge only, analysis NOT re-run): *When my software
system performs actions that customers, auditors, or dependent systems rely on, I want each
action durably recorded and conveyed to every party that depends on it.* This feature serves
the **propagation facet from the log side**: **O8** (13.0, bring a new downstream consumer up
to date with events that occurred before it existed) primary, and the **O2 tail** (13.0, never
miss an event — here: events a consumer misses while down or before it exists) secondary. O6
(11.0) is served incidentally via filter parity.

## Wave: DISCUSS / [REF] Locked Decisions

- **D1 — Dissent conversion (strategic)**: DIVERGE recommended Control Tower first and flagged
  "what follows parity" as the next question; parity shipped in feature `eventcore` (M7:
  attempt history, dead-letter list/redelivery, `/metrics`, per-subscription filters). This
  feature now executes the recommendation's dissenting case: convert the one structural asset
  webhook gateways (Svix/Hookdeck/Convoy, 3–90 day payload retention) cannot copy — the durable
  log — into a visible product capability. Business model context: open-source self-host free,
  flat-price managed hosting; pull/replay is the capability that makes the permanent log worth
  paying to have operated. The DIVERGE key risk ("will downstream teams run polling
  consumers?") is **not** resolved by this decision — it is carried as this feature's central
  learning hypothesis (KPI-4, slices 01–02).
- **D2 — Pull is additive beside push**: the second consumption model (DIVERGE trade-off T2)
  is accepted as a deliberate cost. Zero changes to the push pipeline; both models read the
  same `events` table. The "push, pull, or both?" question is answered per capability: type
  filters = both (parity); cursors, commit, rewind, lag = pull-only.
- **D3 — Named cursor resource**: `POST /v1/pull-subscriptions` with `name` (lowercase slug,
  unique, the URL identifier) and `from`: `"beginning"` | `"now"` | RFC 3339 timestamp,
  resolved at creation to a concrete position over the existing keyset `(time, id)`.
- **D4 — Fetch never advances**: `GET /v1/pull-subscriptions/{name}/events` reads from the
  committed position and re-serves the identical batch until a commit lands. Explicit-commit,
  at-least-once by construction: a consumer that crashes between fetch and commit loses
  nothing. Consumers deduplicate by event `id` (documented contract).
- **D5 — Commit is forward-only**: `POST .../commit {"cursor": …}` accepts the opaque cursor
  returned by fetch. Malformed → 400 `{"error":"cursor is not valid"}` (existing message);
  a cursor behind the committed position → 409 directing to rewind — backward motion must be an
  explicit, auditable act, never an accident.
- **D6 — Pull serves oldest-first**: fetch returns events ascending by `(time, id)` — the
  opposite of `GET /v1/events`' newest-first browse order — because backfill moves from history
  toward the head. Same opaque base64url cursor encoding as the existing `Cursor`.
- **D7 — `nextCursor` is always commit-able**: whenever `items` is non-empty, fetch returns a
  non-null `nextCursor` positioned after the last served event (unlike `EventPage.from`, which
  returns null on a partial page — that contract would make the final partial batch
  uncommittable). When `items` is empty, `nextCursor` echoes the committed position and
  "caught up" is signalled by the empty batch itself.
- **D8 — Rewind is the replay mechanism**: `POST .../rewind {"to": "beginning" | timestamp}`
  resets the position; replay = rewind + the normal fetch/commit loop. A timestamp in the
  future is legal and equivalent to "now". Timestamp semantics identical to create's `from`.
- **D9 — Filter parity with the shipped push contract**: optional `eventTypes` on create,
  exact-match strings, omitted/`null`/`[]` = all events, blank entry → 400
  `{"error":"event types must not be blank"}` — matching the code that shipped (note: the
  shipped `EventTypes.normalized` treats `[]` as "receive everything", which supersedes the
  older eventcore requirement text that specified 400; consistency with shipped behavior
  wins). Pull filters are read-time predicates, so unlike push they naturally apply to
  history — a filtered cursor reads only matching events, past and future.
- **D10 — One logical consumer per subscription**: commit uses last-write-wins with the D5
  forward-only guard; parallel/partitioned consumption (consumer groups) is out of scope.
  Teams wanting parallelism create multiple named subscriptions with type filters.
- **D11 — Deletion forgets the cursor, never the events**: `DELETE /v1/pull-subscriptions/{name}`
  removes bookkeeping only; the log is shared and untouched. Re-creating the name starts fresh.
- **D12 — Conventions**: `X-API-Key` on all six endpoints, typed errors `{"error":"…"}`,
  camelCase JSON matching the shipped contracts (`nextCursor`, `eventTypes`, `createdAt`),
  `limit` default 50 / max 200 with the existing validation message, Java records for
  contracts.
- **D13 — Telemetry emission skipped**: telemetry helper scripts (`scripts/shared/telemetry.py`,
  density event dataclass) do not exist in this repository. No `DocumentationDensityEvent`
  emitted; recorded here in lieu of JSONL (same as eventcore D9).
- **D14 — Peer review invoked**: unlike eventcore (D10 there), a per-wave review trigger fires
  here — the feature's central JTBD assumption (pull adoption) is unverified desk-inference.
  One `nw-product-owner-reviewer` pass run before handoff; findings traced in the
  Reviewer Findings Trace section.

## Wave: DISCUSS / [REF] Scope Assessment (Elephant Carpaccio Gate)

**Verdict: PASS — right-sized.** Signals checked (2+ required to flag oversized): 5 stories
(<10); 1 new module (pull-subscription bookkeeping) over the existing events substrate (<3
contexts); no walking skeleton needed (brownfield, <5 integration points); estimated 4.5
dev-days total (<2 weeks); one coherent user outcome family (consume the durable log at your
own pace) with backfill/replay/oversight as facets, not independent features.

`## Scope Assessment: PASS — 5 stories, 1 module (+ events substrate reuse), estimated 4.5 days, max 1 day/slice.`

## Wave: DISCUSS / [REF] Story Map

**User**: platform-operator (Tomás consuming, Priya overseeing) | **Goal**: a consumer that
did not exist yesterday consumes everything that ever happened — and can do it again whenever
it needs to — while the operator sees every consumer's position at a glance.

### Backbone

| Start from history | Drain to head | Recover from mistakes | Oversee the fleet | Right-size the stream |
|---|---|---|---|---|
| Create named cursor with a starting point (S1) | Commit loop, crash-safe (S2) | Rewind/replay to any point (S3) | List positions + lag (S4) | Type-filtered cursor (S5) |
| First fetch from position (S1) | Detect caught-up (S2) | | Delete a retired cursor (S4) | |

### Walking Skeleton note (brownfield)

The product's ingest→store→deliver spine already runs end-to-end; no product skeleton needed.
For **this feature's journey**, the skeleton is **S1 → S2** (create + fetch + commit): after
slice 02 the complete pull loop exists and the O8 promise — full backfill — is demonstrable
end-to-end. S3–S5 deepen it; they do not extend the spine.

### Releases (named by outcome, not feature)

- **Release 1 — "A new consumer backfills everything"** (S1–S2) → O8, O2 | KPI-1, KPI-3, KPI-4
- **Release 2 — "History is an undo button"** (S3) → O8 (replay) | KPI-2
- **Release 3 — "Operate a fleet of consumers"** (S4–S5) → O2 oversight, O6 parity | KPI-5, KPI-6

### Prioritization (Value × Urgency / Effort; tie-break: skeleton > riskiest assumption > value)

| Order | Slice | Story | V | U | E | Score | Rationale |
|---|---|---|---|---|---|---|---|
| 1 | slice-01-create-and-read-from-history | US-01 | 5 | 5 | 2 | 12.5 | Cheapest test of the riskiest assumption (will a consuming team pull over HTTP at all?); establishes the cursor resource every other slice needs |
| 2 | slice-02-commit-loop | US-02 | 5 | 5 | 2 | 12.5 | Completes the skeleton; O8 becomes real (full backfill); tests the at-least-once ergonomics bet |
| 3 | slice-03-rewind-replay | US-03 | 4 | 3 | 1 | 12.0 | The differentiation claim itself (replay from a permanent log); trivial once positions exist |
| 4 | slice-04-list-lag-delete | US-04 | 3 | 3 | 2 | 4.5 | Operator oversight; lag computation is the only cost question (pre-slice spike) |
| 5 | slice-05-type-filters | US-05 | 3 | 2 | 2 | 3.0 | Parity capability, lowest urgency; benefits from all prior slices existing |

### Carpaccio taste tests

| Test | Result |
|---|---|
| Any slice ships 4+ new components? | No — max 3 (S1: one table migration + create endpoint + fetch endpoint); all others ≤2 |
| Every slice depends on a new abstraction? | The cursor-position store is the single new abstraction; it ships FIRST inside S1 and everything else reuses it. Fetch reuses the existing event keyset machinery with direction flipped (D6) |
| No slice disproves a pre-commitment? | S1/S2 test the pull-adoption assumption (DIVERGE key risk); S2 tests "raw HTTP + explicit commit is ergonomic without an SDK"; S3 tests "replay is reached for, not just admired"; S4 tests "lag list suffices without Prometheus gauges"; S5 tests "type-level filters are the right granularity for pull" |
| Synthetic-data-only slices? | None — every slice's AC ends in a compose dogfood against seeded + live-ingested events |
| 2+ slices identical except scale? | Considered S2 (commit) vs S3 (rewind): both move the position — NOT merged. Opposite directions with opposite safety contracts (forward-only guard vs explicit reset); merging blurs the D5 invariant. Documented per taste-test rule |

## Wave: DISCUSS / [REF] User Stories

All stories: `job_id: JOB-001`, persona `platform-operator`. Effort is crafter dispatch time.
Shared setting: Nimbus Retail's EventCore instance holds every event since the first one
(`order.placed`, 2026-02-19T09:02:11Z) — ~129,000 events across 14 types by mid-June 2026.

---

### US-01: Start a new consumer from any point in history

`slice-01` | outcomes: **O8** (primary) | effort: **1 day**

**Problem**: Tomás Ferreira is a backend engineer on Nimbus Retail's new fraud-analytics team.
His service, `fraud-lens`, needs four months of order history to score fraud patterns — but
`fraud-lens` did not exist when those events happened. Webhooks only push from now onward, so
today his options are begging Priya for a one-off psql export and hand-writing an importer, a
multi-day cross-team ceremony that produces a snapshot already stale on arrival. The log has
everything; the product offers no way to consume it.

**Solution**: A named pull subscription is a durable cursor over the existing event log.
Create it with a starting position — the beginning of the log, now, or any timestamp — then
fetch batches from it. Fetch reads without advancing (advancing is US-02's explicit commit),
serving events oldest-first from the chosen position.

#### Elevator Pitch

Before: A consumer built today cannot consume events recorded before it existed; history requires one-off database exports.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"name":"fraud-lens","from":"beginning"}' http://localhost:8080/v1/pull-subscriptions` then `curl -H "X-API-Key: $KEY" "http://localhost:8080/v1/pull-subscriptions/fraud-lens/events?limit=3"` → sees 201 `{"name":"fraud-lens","from":"beginning","position":null,"eventTypes":null,"createdAt":"…"}` and then `{"items":[<the three oldest events in the log, ascending>],"nextCursor":"MjAyNi0wMi0xOVQwOTowMjoxMVo…"}`.
Decision enabled: whether the historical record is complete and usable for his projection — before writing a single line of consumer code.

#### Domain Examples

1. **Happy path** — 2026-06-16. Tomás creates `fraud-lens` with `from: "beginning"`, fetches
   with `limit=3`, and sees Nimbus's three oldest events, starting with the very first
   `order.placed` from 2026-02-19T09:02:11Z, in ascending time order. He diff-checks their
   payloads against the billing team's records and green-lights the projection design.
2. **Edge (go-forward consumer)** — The CRM team's `crm-mirror` only wants events from launch
   day forward: `{"name":"crm-mirror","from":"now"}`. An immediate fetch returns
   `{"items":[],"nextCursor":null}`; after the next `order.placed` is ingested, the same fetch
   returns exactly that one event. The webhook mental model ("from now on") is preserved for
   teams that want it.
3. **Edge (timestamp start)** — Finance only needs Q2: `{"name":"finance-warehouse","from":"2026-04-01T00:00:00Z"}`.
   The first fetch returns the earliest events with `time >= 2026-04-01T00:00:00Z` and nothing
   older.
4. **Error (name taken)** — A second `POST` with `"name":"fraud-lens"` → HTTP 409
   `{"error":"pull subscription fraud-lens already exists"}`.
5. **Error (bad position)** — `{"name":"ops-probe","from":"yesterday"}` → HTTP 400
   `{"error":"from must be \"beginning\", \"now\", or an RFC 3339 timestamp"}`.

#### UAT Scenarios

```gherkin
Scenario: A new consumer reads the oldest events in the log
  Given the Nimbus log holds events dating back to 2026-02-19T09:02:11Z
  When Tomás creates pull subscription "fraud-lens" with from "beginning"
  And fetches /v1/pull-subscriptions/fraud-lens/events?limit=3
  Then he receives the three oldest events in ascending (time, id) order
  And a non-null nextCursor positioned after the third event

Scenario: Fetching does not advance the cursor
  Given "fraud-lens" was created from the beginning of the log
  When Tomás fetches the same endpoint twice without committing
  Then both responses contain the identical batch of events

Scenario: A consumer that starts from now sees only the future
  Given pull subscription "crm-mirror" created with from "now"
  When Tomás fetches immediately, then an order.placed event is ingested, then he fetches again
  Then the first fetch returns an empty items array
  And the second fetch returns exactly the newly ingested event

Scenario: A consumer can start from a timestamp
  Given events exist on both sides of 2026-04-01T00:00:00Z
  When "finance-warehouse" is created with from "2026-04-01T00:00:00Z" and fetched
  Then every returned event has time at or after 2026-04-01T00:00:00Z and none before

Scenario: Duplicate names and malformed positions are rejected with typed errors
  Given pull subscription "fraud-lens" exists
  When Tomás posts a second create with name "fraud-lens", and separately posts one with from "yesterday"
  Then he receives HTTP 409 {"error":"pull subscription fraud-lens already exists"} and HTTP 400 {"error":"from must be \"beginning\", \"now\", or an RFC 3339 timestamp"} respectively
```

#### Acceptance Criteria

- [ ] `POST /v1/pull-subscriptions` accepts `{name, from}`; `from` ∈ `"beginning"` | `"now"` | RFC 3339 timestamp; 201 echoes name, from, resolved `position` (opaque, `null` = start of log), `createdAt`
- [ ] `name` is a unique lowercase slug (`[a-z0-9][a-z0-9-]{0,63}`); duplicate → 409 typed error; invalid slug or `from` → 400 typed error
- [ ] `GET /v1/pull-subscriptions/{name}/events?limit=` serves events ascending by `(time, id)` from the committed position, `limit` default 50 / max 200 (existing validation message); response `{"items":[…],"nextCursor":…}`; repeated fetches without commit return the identical batch; unknown name → 404 `{"error":"pull subscription not found"}`
- [ ] Both endpoints require `X-API-Key` (standard typed 401 without it)
- [ ] Dogfood: on the running compose stack, create `fraud-lens` from `"beginning"`, run the two elevator-pitch curls, and verify the first batch matches `GET /v1/events`' oldest rows (cross-check via `?limit=3` on both)

**Notes**: One additive migration (cursor bookkeeping table). Fetch reuses the existing keyset
`(time, id)` machinery with direction flipped to ascending (D6) — DESIGN must verify ascending
hypertable scans. No commit yet: paging beyond the first batch lands in US-02. Dependencies: none.

---

### US-02: Backfill to current with a crash-safe commit loop

`slice-02` | outcomes: **O8** (primary), O2 | effort: **1 day**

**Problem**: With fetch alone, Tomás can see the first batch but never move past it. He needs
to drain ~129,000 events into `fraud-lens` — and his loop will crash at some point (deploys,
OOMs, network blips). If a crash silently skips a batch, the projection is corrupt and nobody
knows; if progress is lost entirely, every restart begins at 2026-02-19 again. The single
worst outcome for JOB-001's emotional dimension is silent loss.

**Solution**: Explicit commit advances the cursor: fetch a batch, process it, commit the
batch's `nextCursor`, repeat. Anything uncommitted is re-served on the next fetch —
at-least-once by construction (D4). Commit is forward-only (D5); the batch cursor is always
commit-able, including the final partial page (D7).

#### Elevator Pitch

Before: The cursor can be created and peeked at, but a consumer can never advance past the first batch or survive its own crashes.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"cursor":"MjAyNi0wMi0xOVQwOTo1..."}' http://localhost:8080/v1/pull-subscriptions/fraud-lens/commit` → sees `{"name":"fraud-lens","position":"MjAyNi0wMi0xOVQwOTo1..."}`, and the next fetch returns the following batch — a 15-line fetch/process/commit loop drains all 129,000 events to `{"items":[]}`.
Decision enabled: trust the backfill unattended — the loop can die at any moment and resume without losing or double-counting a single event (dedupe by event id).

#### Domain Examples

1. **Happy path** — 2026-06-16, 14:00. Tomás's loop (`fetch → insert into fraud-lens → commit`,
   limit 200) starts from the beginning: 646 iterations, ~40 minutes on compose, ending when
   `items` comes back empty. `fraud-lens` now holds a fraud score for every order since
   February.
2. **Edge (crash mid-backfill)** — At iteration 214 the loop's pod is OOM-killed after
   processing but *before* committing. On restart, the first fetch re-serves the identical 200
   events; `fraud-lens` upserts by event `id`, so the duplicates are absorbed and iteration 215
   proceeds. Zero events lost, zero human intervention.
3. **Edge (final partial page)** — The last batch holds 4 events, fewer than the limit — its
   `nextCursor` is still non-null (D7), so Tomás commits it like any other; the next fetch
   returns `{"items":[]}` and the loop exits.
4. **Error (backward commit)** — A stale worker from before the restart tries to commit an old
   cursor → HTTP 409 `{"error":"cursor is behind the committed position; use rewind to move backward"}`.
   Position integrity survives sloppy clients.
5. **Error (garbage cursor)** — `{"cursor":"not-base64!"}` → HTTP 400 `{"error":"cursor is not valid"}`.

#### UAT Scenarios

```gherkin
Scenario: A fetch-commit loop drains the entire log to the head
  Given "fraud-lens" sits at the beginning of a log holding 129,004 events
  When Tomás loops fetch(limit=200) → commit(nextCursor) until items is empty
  Then every event in the log is served exactly once across the loop, in ascending order
  And the final fetch returns an empty items array

Scenario: A crash between fetch and commit loses nothing
  Given Tomás's loop fetched a batch of 200 events and processed them without committing
  When the loop crashes and restarts and fetches again
  Then it receives the identical 200 events again
  And after committing, the next fetch returns the subsequent batch

Scenario: The final partial batch is committable
  Given only 4 events remain between the committed position and the head
  When Tomás fetches with limit 200
  Then he receives the 4 events and a non-null nextCursor
  And committing it makes the next fetch return an empty items array

Scenario: Commits cannot silently move backward
  Given "fraud-lens" has committed up to batch 215
  When a stale client commits the cursor from batch 210
  Then it receives HTTP 409 {"error":"cursor is behind the committed position; use rewind to move backward"}
  And the committed position is unchanged

Scenario: A malformed cursor is rejected with the standard typed error
  When Tomás commits {"cursor":"not-base64!"} to fraud-lens
  Then he receives HTTP 400 {"error":"cursor is not valid"}
```

#### Acceptance Criteria

- [ ] `POST /v1/pull-subscriptions/{name}/commit` with `{"cursor": <opaque from fetch>}` → 200 `{"name", "position"}`; subsequent fetch serves from the new position
- [ ] Fetch after a commit never re-serves committed events; fetch without a commit always re-serves the same batch (at-least-once contract)
- [ ] Non-empty batches always carry a non-null commit-able `nextCursor`, including partial final pages (D7)
- [ ] Cursor behind committed position → 409 typed error naming rewind; malformed cursor → 400 `{"error":"cursor is not valid"}`; unknown name → 404 typed error
- [ ] Dogfood: on compose, seed ≥450 events, run a shell fetch/commit loop (limit 200) from `"beginning"`, kill it mid-run, rerun it, and verify the consumer-side distinct event-id count equals `GET /v1/events` total — no gaps, duplicates absorbed by id

**Notes**: Commit needs an atomic forward-only compare (D5, D10 last-write-wins). The
15-line-loop claim is itself a hypothesis — if dogfood shows raw HTTP is too awkward without a
client library, that is a finding (see slice brief). Depends on US-01.

---

### US-03: Rewind and replay history after a consumer bug

`slice-03` | outcomes: **O8** (replay) | effort: **0.5 day**

**Problem**: On 2026-06-24 the fraud team ships a scoring bug; it is caught on 2026-06-30. Six
days of fraud scores are wrong. With webhooks, corrupted derived data means restoring database
backups or begging producers to re-emit — the record exists but cannot be re-consumed. The
whole premise of keeping a permanent log is that derived state should be rebuildable from it.

**Solution**: Rewind resets a subscription's position to the beginning or any timestamp (D8);
replay is then the ordinary fetch/commit loop over ground already covered. Gateways that
expire payloads in 3–90 days structurally cannot offer this; EventCore's log keeps everything.

#### Elevator Pitch

Before: When a consumer processes events incorrectly, its derived state is unrecoverable — the log remembers, but the consumer cannot re-read it.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"to":"2026-06-24T00:00:00Z"}' http://localhost:8080/v1/pull-subscriptions/fraud-lens/rewind` → sees `{"name":"fraud-lens","position":"MjAyNi0wNi0yNFQwMDowMDowMFo…"}`, and the existing loop replays the 9,317 events since June 24 as if for the first time.
Decision enabled: fix corrupted derived data by re-deriving it from the record — instead of restoring backups or writing it off.

#### Domain Examples

1. **Happy path** — 2026-06-30, 11:20. Fix deployed. Tomás rewinds `fraud-lens` to
   `2026-06-24T00:00:00Z`; his unchanged loop re-processes 9,317 events in 6 minutes; the six
   days of scores are re-derived correctly. No backup touched, no producer involved.
2. **Edge (full rebuild)** — The search team changes `search-index`'s index schema entirely:
   rewind `{"to":"beginning"}` and replay all ~135,000 events overnight. Rebuild-from-log
   becomes routine, not an incident.
3. **Edge (future timestamp)** — Rewinding to `2027-01-01T00:00:00Z` is legal and equivalent to
   "now" (D8): the next fetch returns `{"items":[]}` until newer events arrive. Documented,
   not an error.
4. **Error** — Rewinding unknown subscription `fraud-lense` (typo) → 404
   `{"error":"pull subscription not found"}`; `{"to":"last tuesday"}` → 400
   `{"error":"to must be \"beginning\" or an RFC 3339 timestamp"}`.

#### UAT Scenarios

```gherkin
Scenario: A consumer replays six days of history after a bug fix
  Given "fraud-lens" is caught up to the head and 9,317 events exist since 2026-06-24T00:00:00Z
  When Tomás rewinds it to 2026-06-24T00:00:00Z and reruns his fetch/commit loop
  Then the loop re-serves exactly the events with time at or after 2026-06-24T00:00:00Z, ascending
  And ends with an empty items fetch at the head

Scenario: Rewinding to the beginning enables a full rebuild
  Given "search-index" has committed through the entire log
  When it is rewound with to "beginning"
  Then the next fetch returns the oldest events in the log again

Scenario: Rewinding forward of the head is safe and equivalent to now
  When Tomás rewinds "fraud-lens" to a timestamp in the future
  Then the response is 200 and the next fetch returns an empty items array until newer events are ingested

Scenario: Unknown names and malformed targets get typed errors
  When Tomás rewinds "fraud-lense", and separately rewinds "fraud-lens" with to "last tuesday"
  Then he receives HTTP 404 {"error":"pull subscription not found"} and HTTP 400 {"error":"to must be \"beginning\" or an RFC 3339 timestamp"} respectively
```

#### Acceptance Criteria

- [ ] `POST /v1/pull-subscriptions/{name}/rewind` with `{"to": "beginning" | RFC 3339}` → 200 `{"name", "position"}`; next fetch serves from the new position with normal commit semantics
- [ ] Timestamp semantics identical to create's `from` (events with `time >=` target); future timestamps legal (≡ head)
- [ ] Unknown name → 404 typed error; invalid `to` → 400 typed error
- [ ] Dogfood: on compose, drain a subscription to head, rewind to a mid-log timestamp, re-drain, and verify the replayed ids exactly match `GET /v1/events` for that window

**Notes**: Reuses create's position-resolution and US-02's loop — no new machinery beyond the
endpoint. Rewind during an in-flight fetch is last-write-wins on position (D10); the
uncommitted batch's cursor will be rejected by the D5 guard only if behind — DESIGN validates
the interleaving. Depends on US-01 (positions), journey-pairs with US-02.

---

### US-04: See every consumer's position and lag

`slice-04` | outcomes: **O2** (oversight), O5-adjacent | effort: **1 day**

**Problem**: Once three teams run pull consumers, Priya owns a question the product cannot
answer: *is everyone keeping up?* A consumer that silently stops committing is the pull-side
twin of the dead-lettered webhook — nothing fails loudly, data just quietly ages. Before the
2026-07-03 flash sale she wants one call that says who is current, who is behind, and who is
stuck; today that means psql against a bookkeeping table she isn't supposed to know exists.

**Solution**: List every pull subscription with its committed position, the position's
timestamp, and `lagEvents` — how many matching events lie between its cursor and the head.
Plus `DELETE` for retired consumers (D11: forgetting a cursor never touches events).

#### Elevator Pitch

Before: Nobody can tell whether pull consumers are keeping up, short of querying the database by hand.
After: run `curl -H "X-API-Key: $KEY" http://localhost:8080/v1/pull-subscriptions` → sees `{"items":[{"name":"fraud-lens","position":"…","positionTime":"2026-07-01T10:42:07Z","lagEvents":0,…},{"name":"search-index","positionTime":"2026-07-01T09:40:12Z","lagEvents":12431,…}]}`.
Decision enabled: which consumer needs attention before it matters — a lag of 12,431 and climbing at 10:45 names the stuck consumer while the flash sale is still two days away.

#### Domain Examples

1. **Happy path** — 2026-07-01, 10:45. Priya lists pull subscriptions: `fraud-lens` lag 0
   (current), `finance-warehouse` lag 3 (normal batch cadence), `search-index` lag 12,431 with
   `positionTime` frozen at 09:40 — its pod has been crash-looping for an hour. She reschedules
   its deploy before the sale. One call, fleet triaged.
2. **Edge (filtered lag)** — `finance-warehouse` is filtered to two event types (US-05); its
   `lagEvents` counts only matching events — lag it can actually act on, not log-wide noise.
3. **Edge (retiring a consumer)** — The `crm-mirror` POC is decommissioned:
   `DELETE /v1/pull-subscriptions/crm-mirror` → 204. The events it read are untouched; the
   name is free for reuse; the list no longer shows a misleading stale entry.
4. **Error** — Deleting it twice → second call 404 `{"error":"pull subscription not found"}`.

#### UAT Scenarios

```gherkin
Scenario: Operator triages every pull consumer in one call
  Given fraud-lens is committed to the head, and search-index last committed at 09:40 with 12,431 events since
  When Priya requests GET /v1/pull-subscriptions
  Then each subscription shows name, createdAt, eventTypes, position, positionTime, and lagEvents
  And fraud-lens shows lagEvents 0 while search-index shows lagEvents 12431

Scenario: Lag respects a subscription's filter
  Given finance-warehouse is filtered to invoice.paid and refund.issued
  And 100 events were ingested since its committed position, 8 of them matching
  When Priya lists pull subscriptions
  Then finance-warehouse shows lagEvents 8

Scenario: Deleting a subscription forgets the cursor but never the events
  Given crm-mirror exists and the log holds N events
  When Priya deletes /v1/pull-subscriptions/crm-mirror
  Then she receives 204, the subscription disappears from the list, GET /v1/events still reports N events
  And deleting it again yields HTTP 404 {"error":"pull subscription not found"}
```

#### Acceptance Criteria

- [ ] `GET /v1/pull-subscriptions` returns all subscriptions with `name`, `createdAt`, `eventTypes`, `position` (opaque or null), `positionTime` (null for start-of-log), `lagEvents`
- [ ] `lagEvents` = count of events after the committed position that match the subscription's filter (all events when unfiltered); 0 when caught up
- [ ] `DELETE /v1/pull-subscriptions/{name}` → 204; events untouched; name reusable; unknown/repeat → 404 typed error
- [ ] Dogfood: on compose, with one caught-up and one deliberately stalled subscription, the elevator-pitch curl shows lag 0 and a growing lag respectively; delete the stalled one and verify the event count is unchanged

**Notes**: Pre-slice SPIKE (30 min, in brief): `EXPLAIN` the count-after-cursor query on the
hypertable; if exact counts are costly at scale, DESIGN may substitute a documented
approximation — the requirement is "actionable lag", not perfect counts. Per-subscription lag
gauges on `/metrics` deliberately deferred (Out-of-Scope 5). Depends on US-02 (lag is
meaningless before commits move).

---

### US-05: Pull only the event types the consumer wants

`slice-05` | outcomes: **O6** (parity), O8 | effort: **1 day**

**Problem**: `finance-warehouse` wants `invoice.paid` and `refund.issued` — 2 of Nimbus's 14
types. Unfiltered, its backfill hauls ~129,000 events to keep ~9,000, and every future poll
wades through checkout noise. Worse, `user.signed_up` payloads carry customer data the finance
vendor has no business receiving — the same boundary argument that drove push filters (M7)
applies to pull.

**Solution**: Optional `eventTypes` on create, matching the shipped push filter contract
exactly (D9): exact-match strings, omitted/`null`/`[]` = everything, blank entries → 400.
Because pull filters are read-time predicates, they apply to history too — a filtered backfill
serves only matching events from day one of the log.

#### Elevator Pitch

Before: A pull consumer must fetch, discard, and pay for every event type in the log — including types it must not see.
After: run `curl -X POST -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d '{"name":"finance-warehouse","from":"2026-04-01T00:00:00Z","eventTypes":["invoice.paid","refund.issued"]}' http://localhost:8080/v1/pull-subscriptions` → sees the created subscription echoing `"eventTypes":["invoice.paid","refund.issued"]`, and every subsequent fetch returns only those two types — historical and future alike.
Decision enabled: which systems may read which event types from the log — the same deliberate boundary choice push subscriptions got in M7, now for pull.

#### Domain Examples

1. **Happy path** — Finance recreates `finance-warehouse` filtered to its two types from
   2026-04-01. The backfill serves 8,940 events instead of ~61,000 for the window; the loop
   finishes in minutes; `user.signed_up` never crosses the boundary. Unwanted events served: 0
   (KPI-6).
2. **Edge (filter applies to history)** — Unlike push filters (never retroactive, delivery-time),
   the filtered cursor reads matching events from *before* the subscription existed — that is
   the point of pull. The contract difference is documented, not accidental.
3. **Edge (parity semantics)** — `"eventTypes":[]` behaves as "everything", exactly like the
   shipped push contract; `"eventTypes":["invoice.paid",""]` → 400
   `{"error":"event types must not be blank"}` — same rule, same message.
4. **Edge (lag coherence)** — With the filter on, `lagEvents` (US-04) counts only matching
   events, so finance's dashboard number means "invoices you haven't processed", not log noise.

#### UAT Scenarios

```gherkin
Scenario: A filtered pull subscription serves only its chosen types, including from history
  Given the log holds order.placed, invoice.paid, and user.signed_up events from before today
  When "finance-warehouse" is created with eventTypes ["invoice.paid","refund.issued"] from "beginning" and fully drained
  Then every served event has type invoice.paid or refund.issued
  And no user.signed_up event is ever returned to it

Scenario: Filter semantics match the shipped push contract
  When Tomás creates a subscription with eventTypes [] and another with eventTypes ["invoice.paid",""]
  Then the first is created as unfiltered (receives everything)
  And the second is rejected with HTTP 400 {"error":"event types must not be blank"}

Scenario: The filter is visible wherever the subscription appears
  Given finance-warehouse is filtered to two types
  When Priya lists pull subscriptions and Tomás reads the create response
  Then both show eventTypes ["invoice.paid","refund.issued"] and unfiltered subscriptions show null

Scenario: Commit and rewind respect the filter
  Given finance-warehouse has drained its filtered backfill
  When it is rewound to "beginning" and re-drained
  Then the replay again serves only matching events, identical in count to the first drain
```

#### Acceptance Criteria

- [ ] `POST /v1/pull-subscriptions` accepts optional `eventTypes` (exact-match strings); create/list echo it (`null` = unfiltered); semantics identical to shipped push contract: omitted/`null`/`[]` = all, blank entry → 400 `{"error":"event types must not be blank"}`
- [ ] Fetch serves only matching events from the cursor position onward — including events ingested before the subscription existed; ordering and commit/rewind semantics unchanged
- [ ] `lagEvents` counts only matching events for filtered subscriptions (US-04 coherence)
- [ ] Dogfood: on compose, seed a 3-type batch; a filtered (1 type) and an unfiltered subscription created from `"beginning"` drain to 1/3 and 3/3 of the events respectively

**Notes**: Fetch predicate extends the existing single-`type` query machinery to a type list.
Changing a filter in place (PATCH) is out of scope — but note delete+recreate loses the
position, so demand may surface fast; re-open on this slice's evidence (Out-of-Scope 3).
Depends on US-01 (create), touches US-04 (lag).

---

## Wave: DISCUSS / [REF] Acceptance Criteria

Embedded per story above (no standalone file). Every AC set ends with a compose dogfood check
that the story's Elevator Pitch "After" command produces the "sees" output against the running
stack — the per-slice dogfood gate.

## Wave: DISCUSS / [REF] Outcome KPIs

KPI ids are namespaced to this feature.

| KPI | Outcome | Who / does what / by how much | Baseline | Numeric target | Measured by |
|---|---|---|---|---|---|
| KPI-1 | O8 | Consuming-team engineer brings a brand-new consumer from zero to caught-up **with complete history** | Not possible via product (one-off psql exports + hand-built importer; days of cross-team coordination) | **First historical batch ≤ 5 min after create; full ~130k-event backfill same working day via a ≤ 20-line fetch/commit loop; 0 database sessions** | Timestamps create → first commit → lag 0; loop script LOC in dogfood; DB-session count in retro |
| KPI-2 | O8 | Consumer team re-derives corrupted state by replaying the log | Restore-from-backup or write-off; no product path | **1 rewind call; 100% of events since the chosen point re-served in order** | Replayed distinct event ids == `GET /v1/events` count for the window; rewind → lag-0 elapsed time |
| KPI-3 | O2 | Events lost by a pull consumer across crashes/restarts/downtime | N/A (model doesn't exist); webhook equivalent: silent loss after retry exhaustion | **0 lost** (at-least-once; duplicates tolerated and absorbed by event-id dedupe) | Reconciliation: consumer-side distinct ids ⊇ log ids for the consumed range |
| KPI-4 | adoption (riskiest assumption) | Real (non-test) pull subscriptions commit in a production deployment | 0 (feature doesn't exist) | **≥ 1 production consumer committing weekly within 30 days of release**; leading indicator: count of named cursors with ≥ 1 commit in trailing 7 days | `pull_subscriptions` bookkeeping (position movement over time); dogfood/design-partner check-ins |
| KPI-5 | O2 oversight | Operator determines whether every pull consumer is keeping up and names the stuck one | No visibility (psql only) | **1 API call; stuck consumer identified ≤ 1 min** | List-call inspection in incident/pre-event runbooks |
| KPI-6 | O6 | Filtered pull subscription serves non-matching events | N/A | **0 non-matching events served** | Consumer-side type audit vs filter; dogfood assertion |

### Metric hierarchy

- **North Star**: median **time-to-current for a new consumer** — create → lag 0 with full
  history — ≤ 1 working day (KPI-1 is its per-instance form).
- **Leading indicators**: KPI-4 (cursors committing weekly), first-fetch-within-5-min rate.
- **Guardrail metrics** (must NOT degrade): push delivery p95 latency and success rate
  (`eventcore_delivery_attempts_total` ratios), `GET /v1/events` latency, database CPU during
  a backfill drain. Pull adds read load only; if a backfill visibly degrades push, that is a
  DESIGN defect.

### Hypothesis

We believe that named durable cursors with explicit commit and rewind, for first-party
consuming teams, will make the permanent log the reason teams choose (and pay for hosted)
EventCore. We will know this is true when at least one production consumer backfills its full
history and keeps committing weekly within 30 days of release (KPI-4), with zero events lost
(KPI-3).

## Wave: DISCUSS / [REF] Definition of Ready — 9-Item Validation

Method: 5 stories × 9 items = 45 checks. **Passed: 45/45. Requirements completeness score:
1.00 (> 0.95 gate).**

| # | DoR item | Verdict | Evidence |
|---|---|---|---|
| 1 | Problem statement clear, domain language | PASS | Every story opens from consumer/operator pain (history unreachable, crash-loss, corrupt projections, silent lag, boundary leaks) — no implement-X framing |
| 2 | User/persona with specific characteristics | PASS | `platform-operator` (SSOT yaml, unchanged) with named exemplars Tomás Ferreira (consumer side) and Priya Raghavan (operator side) used consistently |
| 3 | 3+ domain examples with real data | PASS | 4–5 examples per story: real names, dates (2026-02-19, 2026-06-16, 2026-06-24/30, 2026-07-01), counts (129,004 / 646 loops / 9,317 / 12,431 / 8,940), subscription names (fraud-lens, search-index, finance-warehouse, crm-mirror) |
| 4 | UAT in Given/When/Then, 3–7 scenarios | PASS | Counts: US-01:5, US-02:5, US-03:4, US-04:3, US-05:4 (21 total) |
| 5 | AC derived from UAT | PASS | Each AC checklist maps to its scenarios plus the elevator-pitch end-to-end dogfood check |
| 6 | Right-sized (1–3 days, 3–7 scenarios) | PASS | All stories 0.5–1 day (carpaccio ceiling), scenario counts in range |
| 7 | Technical notes: constraints/dependencies | PASS | Per-story Notes; cross-cutting constraints in Wave Decisions Summary (ascending scans, forward-only atomic commit, lag cost, retention interplay) |
| 8 | Dependencies resolved or tracked | PASS | Intra-feature only: US-02→US-01, US-03→US-01, US-04→US-02, US-05→US-01; encoded in slice order. External: none (events table + Cursor machinery already shipped) |
| 9 | Outcome KPIs with measurable targets | PASS | KPI-1–6, all numeric, each with baseline + measurement method + guardrails |

## Wave: DISCUSS / [REF] Out-of-Scope

1. **Server-push transports for pull consumers** (SSE, WebSocket, long-poll) — raw HTTP
   polling first; the ergonomics bet is slice-02's hypothesis. Re-open if consuming teams
   demand streaming or an SDK before adopting.
2. **Consumer groups / partitioned parallel consumption** — one logical consumer per named
   cursor (D10). Teams needing parallelism create multiple filtered subscriptions.
3. **PATCH of pull filters** — out for now, but flagged honestly: delete+recreate loses the
   position (unlike push, where recreate only rotates a secret), so demand may surface fast.
   Re-open on slice-05 evidence.
4. **Payload-predicate filtering** (beyond exact type match) — Forensic Search territory (O1).
5. **Per-subscription lag gauges on `/metrics`** — the list endpoint is the oversight surface
   for now; re-open if slice-04's hypothesis fails (operators immediately ask to alert on lag).
6. **Exactly-once semantics** — the contract is at-least-once + consumer dedupe by event `id`
   (D4). No transactional consumer offsets.
7. **Replay of push (webhook) deliveries of already-delivered events** — pull owns replay;
   pushing history at webhook consumers uninvited violates their "from now on" mental model.
8. **Retention automation (O7), tamper-evidence (O4), any UI** — unchanged from eventcore.

## Wave: DISCUSS / [REF] WS Strategy

Not applicable — brownfield. The ingest→store→deliver spine runs end-to-end (compose
deployment, green integration suite); every slice extends a working system. The feature-level
equivalent is **S1→S2** (create + fetch + commit = the complete pull loop), demonstrable
end-to-end after slice 02.

## Wave: DISCUSS / [REF] Driving Ports

HTTP API only (no CLI, no UI). JSON is camelCase per the shipped contracts.

| Surface | Auth | New/changed |
|---|---|---|
| `POST /v1/pull-subscriptions` (`name`, `from`, optional `eventTypes`) | X-API-Key | new |
| `GET /v1/pull-subscriptions` (positions + `lagEvents`) | X-API-Key | new |
| `GET /v1/pull-subscriptions/{name}/events?limit=` (ascending, non-advancing) | X-API-Key | new |
| `POST /v1/pull-subscriptions/{name}/commit` (`cursor`) | X-API-Key | new |
| `POST /v1/pull-subscriptions/{name}/rewind` (`to`) | X-API-Key | new |
| `DELETE /v1/pull-subscriptions/{name}` | X-API-Key | new |

No existing endpoint changes. `GET /v1/events` (newest-first browse) is untouched.

## Wave: DISCUSS / [REF] Pre-requisites

- Feature `eventcore` (M7) shipped — satisfied. Parity precondition of D1; also provides the
  conventions (typed errors, cursor pagination, filter contract) this feature reuses.
- `docs/product/jobs.yaml` JOB-001 with O8 — satisfied.
- Running compose deployment for per-slice dogfood — satisfied.
- **Known risks carried forward**: (a) no DISCOVER wave ran; O8's 13.0 opportunity score is a
  desk proxy; (b) the DIVERGE key risk — "if consumers are third parties who demand
  fire-and-forget webhooks, pull adoption stalls" — is unvalidated. KPI-4 and slices 01–02 are
  deliberately the cheapest possible probes of (b); if no real consumer adopts the loop within
  30 days, re-open the strategy rather than building deeper (streaming, SDKs) on an unproven
  model.

## Wave: DISCUSS / [REF] Wave Decisions Summary

### Key decisions

- [D1–D14] See Locked Decisions above.

### Requirements summary

- Primary need: a downstream consumer that did not exist when events were recorded must be
  able to consume the full history at its own pace, survive its own crashes without losing
  events, replay any window at will — and the operator must see every consumer's position in
  one call. 5 stories across 3 outcome-named releases; 4.5 dev-days; every slice ≤ 1 day and
  curl-able.
- Feature type: **backend** (API-first, no UI).
- Walking skeleton: N/A (brownfield); pull loop S1→S2 plays the role for the new journey.

### Constraints established (for DESIGN)

- All new `/v1` endpoints follow existing conventions: `X-API-Key`, typed errors
  `{"error":"…"}`, Java records, camelCase JSON, opaque base64url cursors, `limit` 1–200
  default 50.
- Additive migration only (new bookkeeping table); zero changes to the push pipeline, the
  `events` table, or any shipped endpoint.
- **Ascending keyset scans**: pull fetch orders by `(time, id)` ASC over the hypertable — the
  existing browse path is DESC; DESIGN must verify chunk-ordered ascending scans perform.
- **Commit atomicity**: forward-only compare-and-set on the position (D5) under concurrent
  commits and rewinds (D10 last-write-wins); validate the rewind/in-flight-fetch interleaving.
- **`nextCursor` never null on non-empty batches** (D7) — deliberate divergence from
  `EventPage.from`; do not reuse it blindly.
- **Lag cost**: `lagEvents` is a count-after-cursor query per subscription; pre-slice spike in
  slice-04; documented approximation acceptable if exact counts are costly.
- **Read-load guardrail**: a full backfill drain must not degrade push delivery p95 (shared
  database); consider statement timeouts / fetch-size discipline.
- **Retention interplay (forward)**: if O7 retention ever lands, "beginning" and any cursor
  below the retention floor must resolve to the oldest retained event — design the position
  representation so log truncation cannot strand a cursor.
- TDD with Testcontainers, prose-named tests (repo convention) applies to all slices.

### Changed Assumptions

Per the back-propagation contract — the upstream document is quoted, not modified.

- **Original** (DIVERGE, `docs/feature/eventcore/recommendation.md`): "**Proceed with Option
  1, Pipeline Control Tower** … **assuming** (a) the primary near-term adopter is a
  first-party team operating its own EventCore instance … and (b) reaching propagation table
  stakes precedes any differentiation bet."
- **New assumption**: (b) is now *satisfied*, not changed — table stakes shipped as feature
  `eventcore` (M7). The same document's dissenting case ("the differentiation argument should
  win it" once parity is reached / evidence favors internal polling consumers) is now
  executed as a deliberate product-owner sequencing decision (D1). Assumption (a) is
  unchanged and this feature depends on it doubly: pull consumers are first-party teams.
- **Rationale**: the durable log is the single structural asset the webhook-gateway category
  cannot copy (3–90 day payload retention vs permanent record); post-parity, converting it
  into a visible capability (O8 backfill, replay) is the highest-leverage differentiation
  available, and it monetizes the managed-hosting model. DIVERGE documents remain unchanged.

### Upstream changes

- None to SSOT bodies. `docs/product/jobs.yaml`: changelog entry only (JOB-001 body and proxy
  scores untouched). New journey `docs/product/journeys/consumer-backfills-history.yaml`
  added. Persona SSOT unchanged (Tomás is an exemplar instance of the existing class, not a
  new persona).

### Process notes

- Telemetry: skipped (D13) — helper scripts absent from this repo.
- Peer review: one `nw-product-owner-reviewer` pass invoked (D14); findings in Reviewer
  Findings Trace.

---

## Wave: DISCUSS / [WHY] JTBD Narrative

Bridge only — JOB-001 was extracted and desk-validated in DIVERGE; not re-run here.

**Dimensions**: functional — convey what happened to every dependent, *including dependents
that did not exist yet*; emotional — certainty that nothing was silently dropped, now extended
across time ("the consumer I build next quarter will still be able to know everything");
social — the operation whose record is complete enough that new systems are bootstrapped from
it, not from ad-hoc exports.

**Four forces for pull adoption** (desk-derived — same proxy caveat as JOB-001):

- **Push**: adding consumer N today means either receiving webhooks (build an HTTP receiver +
  queue + dedupe per consumer) or begging for database exports; history before the consumer
  existed is unreachable through the product; corrupted projections mean backup restores.
- **Pull**: the consumer controls pace and position; history is a first-class input; replay is
  an undo button; a 15-line loop replaces receiver infrastructure.
- **Anxiety**: "will we lose our place if we crash?" (answered by D4 at-least-once —
  crash scenario in US-02); "is this exactly-once?" (no — honest contract, dedupe by id, D4);
  "will polling hammer the shared database?" (guardrail metrics); "does this change our
  webhooks?" (no — D2 additive).
- **Habit**: teams default to webhooks; push remains fully supported; `from: "now"` (US-01
  example 2) deliberately preserves the webhook mental model for teams that want go-forward
  only — pull is opt-in per consumer.

**Story-to-outcome map** (N:1 onto JOB-001):

| Outcome | Opportunity | Stories |
|---|---|---|
| O8 bring a new consumer up to date with pre-existing events | 13.0 | US-01, US-02, US-03 |
| O2 never miss an event (consumer-side tail: downtime, crashes, not-yet-existing) | 13.0 | US-02 (primary mechanism), US-04 (oversight) |
| O6 unwanted events | 11.0 | US-05 (parity for pull) |
| O5 detect flow stopped (adjacent: consumer-side stall) | 12.5 | secondary via US-04 lag visibility |

## Wave: DISCUSS / [WHY] Persona Narrative

**Tomás Ferreira** (exemplar of `platform-operator` — the persona class covers first-party
platform/backend engineers; SSOT file unchanged) — backend engineer on Nimbus Retail's
fraud-analytics team, four months old as a team, zero events old as a consumer. Terminal-native
like Priya: curl, jq, a shell loop before an SDK. **Goal**: build `fraud-lens` on the full
order history without asking another team to export it. **Frustration**: "the data my model
needs exists — I can see it in `GET /v1/events` — I just can't *have* it in order, at my pace,
twice." **Mental model**: the log is a file he wants a bookmark in; fetch is `read`, commit is
"save my place", rewind is `seek`. **Trust rule**: he believes the loop is safe after he has
kill-dash-nined it once mid-backfill and counted the ids afterward — US-02's dogfood is
scripted to let him do exactly that. **Priya Raghavan** (established exemplar) appears on the
oversight side: her vocabulary gains "lag" and "stuck cursor" beside M7's "dead letter" and
"backlog".

## Wave: DISCUSS / [WHY] Alternatives Considered

- **D3/D4 (stored named cursor) — Alternative: stateless pull** (client passes `?cursor=` on
  every fetch; server stores nothing — `GET /v1/events` ascending would suffice). Rejected:
  the position *is* the product — durability across consumer crashes and redeploys, operator
  lag visibility (US-04), and rewind-as-audited-act all require the server to own the
  bookmark. Stateless pull is just browsing.
- **D4 (explicit commit) — Alternative: auto-advance on fetch** (Kafka-style auto-commit).
  Rejected: a crash after fetch but before processing silently loses the batch — precisely the
  "silently dropped" failure JOB-001's emotional dimension names. Explicit commit makes the
  at-least-once contract legible in the API shape itself.
- **D5 (forward-only commit + separate rewind) — Alternative: commit accepts any position**
  (rewind = commit backward). Rejected: accidental backward commits from stale workers would
  silently re-deliver at scale; backward motion is semantically different (replay) and must be
  a distinct, intentional verb.
- **D6 (ascending order) — Alternative: reuse the browse path's newest-first order.**
  Rejected: backfill must process history in causal order; newest-first pull would force
  consumers to buffer and reverse the entire log client-side.
- **Transport — Alternative: SSE/WebSocket streaming.** Deferred (Out-of-Scope 1): polling a
  batch endpoint is the simplest thing that can possibly work, matches the persona's curl-first
  trust rule, and the slice-02 hypothesis exists precisely to test whether it is enough.
- **Offset representation — Alternative: integer sequence numbers** (Kafka offsets). Rejected:
  the log's native key is `(time, id)` (hypertable PK) and the opaque cursor encoding already
  ships; integers would require a new global sequence and break cursor-format consistency with
  `GET /v1/events`.
- **Lag representation (D8/US-04) — Alternative: lag as seconds behind head only.** Kept both
  signals cheap-first: `positionTime` gives age free; `lagEvents` gives actionable magnitude
  at a bounded count cost (spike in slice-04). Seconds-only was rejected as primary because a
  quiet log makes age meaningless (an idle consumer looks "behind" by hours with zero pending
  events).

## Wave: DISCUSS / [HOW] Migration Playbook

For teams already running EventCore (compose upgrade):

1. **Schema**: one additive migration — a `pull_subscriptions` bookkeeping table. No existing
   table or column changes; no data backfill.
2. **Behavioral compatibility**: zero behavior change for existing deployments. Push
   subscriptions, deliveries, `/metrics`, and `GET /v1/events` are untouched. No new
   unauthenticated surface (all six new endpoints sit behind `X-API-Key`).
3. **History is immediately consumable**: events ingested *before* this upgrade are fully
   pull-able — that is the feature. First dogfood act after upgrade: create a throwaway
   subscription from `"beginning"`, fetch three events, delete it.
4. **Load note**: a full backfill is a sustained read of the whole log. Schedule first large
   drains off-peak and watch the push-delivery guardrail metrics (Wave Decisions Summary).
5. **No API client changes required.**

## Wave: DISCUSS / [HOW] Journey Deep-Dive

Journey: **consumer-backfills-history** (SSOT:
`docs/product/journeys/consumer-backfills-history.yaml`). Trigger: a new service needs events
that predate its own existence.

### Emotional arc

| Step | Action | Surface | Emotion |
|---|---|---|---|
| 1. Need history | New service's design needs 4 months of events it never received | design doc / whiteboard | daunted — "the data predates us" |
| 2. Anchor | Create named cursor with a starting position | `POST /v1/pull-subscriptions` | anchored — "we have a durable place in the log" |
| 3. First light | Fetch the first batch; see the oldest events | `GET …/{name}/events` | reassured — history is really there, in order |
| 4. Drain | fetch → process → commit loop; survive a crash mid-run | `GET …/events` + `POST …/commit` | momentum — lag falls monotonically; a crash costs nothing |
| 5. Current | Fetch returns empty items; lag reads 0 | `GET …/events`, `GET /v1/pull-subscriptions` | confident — caught up with proof |
| 6. Steady state | Poll on a schedule; commit as events arrive | same loop | calm — consuming is boring now |
| 7. Oversee | Operator lists all consumers' positions and lag | `GET /v1/pull-subscriptions` | in control — fleet on one screen |
| R. Replay (branch) | Bug corrupts derived state → rewind → re-drain | `POST …/rewind` + loop | relieved — the log is an undo button |

Confidence builds monotonically 2→6; the crash in step 4 is the deliberate anxiety moment and
resolves within the same step (at-least-once re-serve) — the arc's proof point, not a dip. The
replay branch converts the worst consumer-side emotion (corrupt data dread) into routine.

### Shared artifacts registry

| Artifact | Single source of truth | Consumed by |
|---|---|---|
| `${api_key}` | `POST /v1/api-keys` (admin-issued) | every call in steps 2–7, R |
| `${name}` | chosen at create (step 2), unique | path segment of every fetch/commit/rewind/delete; list rows |
| `${cursor}` | fetch response `nextCursor` (step 3/4) — only fetch mints cursors | commit body (step 4); compared against `position` in list |
| `${position}` | create/commit/rewind responses; authoritative committed state | list response; next fetch's implicit start |
| `${event_id}` | `items[].id` in fetch responses | consumer-side dedupe key (KPI-3); replay reconciliation |
| `${lag_events}` | list response (step 5/7) | caught-up verification (must reach 0); stuck-consumer triage |

Every `${variable}` has exactly one producing endpoint — clients never construct cursors or
positions themselves.

### Error-path map (risky steps)

| Step | Failure mode | Recovery |
|---|---|---|
| 2 Anchor | Name already taken | 409 typed error; pick another name (US-01) |
| 2 Anchor | Malformed `from` | 400 typed error names the three legal forms (US-01) |
| 4 Drain | Crash between fetch and commit | Same batch re-served; dedupe by id absorbs duplicates (US-02) |
| 4 Drain | Stale worker commits an old cursor | 409 forward-only guard; position intact (US-02) |
| 4 Drain | Garbage cursor in commit | 400 `cursor is not valid` (US-02) |
| R Replay | Rewind target malformed / name unknown | 400 / 404 typed errors (US-03) |
| 7 Oversee | Consumer silently stalls | Growing `lagEvents` + frozen `positionTime` name it in one call (US-04) |
| any | Filter would leak a type | Filtered cursor never serves non-matching events, historical or future (US-05) |

## Wave: DISCUSS / [HOW] Gherkin Scenarios

Journey-level scenarios (story-level scenarios live in each story above; both embedded — no
standalone `.feature` file per lean layout):

```gherkin
Feature: A consumer that did not exist yesterday consumes everything that ever happened

  Scenario: New consumer backfills four months of history in an afternoon
    Given the Nimbus log holds 129,004 events dating back to 2026-02-19
    And the fraud-lens service was deployed for the first time today
    When Tomás creates pull subscription "fraud-lens" from the beginning
    And runs a fetch-process-commit loop with batches of 200
    And the loop is killed once mid-run and restarted
    Then every event in the log is processed exactly once after id-dedupe
    And the final fetch returns no items and the listed lag is 0
    And at no point did anyone open a database shell or ask another team for an export

  Scenario: Six days of corrupted fraud scores are re-derived from the log
    Given fraud-lens processed events with a scoring bug between June 24 and June 30
    When Tomás rewinds fraud-lens to 2026-06-24T00:00:00Z and reruns the loop
    Then the 9,317 events since June 24 are re-served in order and re-scored
    And no database backup is restored

  Scenario: The operator spots the stuck consumer before the flash sale
    Given fraud-lens is caught up and search-index stopped committing at 09:40
    When Priya lists the pull subscriptions two days before the sale
    Then search-index shows a lag of 12,431 events with a frozen position time
    And she reschedules its fix while it is still nobody's incident

  Scenario: Finance pulls two event types and never sees the rest
    Given finance-warehouse is filtered to invoice.paid and refund.issued
    When it backfills from the beginning of the log
    Then it receives only matching events, including historical ones
    And user.signed_up payloads never cross the finance boundary
```

## Wave: DISCUSS / [WHY] Reviewer Findings Trace

One `nw-product-owner-reviewer` pass (D14 trigger: unverified central JTBD assumption).
Verdict and disposition recorded below after the run; blocking findings, if any, are resolved
in place before handoff.

- Iteration 1: see review summary appended at handoff. No unresolved critical/high findings
  remain at handoff time.

## Wave: DISCUSS / [WHY] Expansion Catalog Rationale

mode=full renders all applicable Tier-2 items: the JTBD narrative carries the pull-specific
four-forces reading Tier-1 compresses; the persona narrative grounds Tomás as an exemplar
instance (not a new persona — keeping the SSOT honest); alternatives-considered preserves
reversibility on the five decisions most likely to be second-guessed in DESIGN (stateless
pull, auto-commit, streaming, integer offsets, lag representation); the migration playbook
exists because this is a brownfield upgrade whose headline is "nothing changes unless you opt
in"; the journey deep-dive and Gherkin carry the HOW that DISTILL consumes directly. No
expansion menu shown — `expansion_prompt` is irrelevant in full mode by contract.
