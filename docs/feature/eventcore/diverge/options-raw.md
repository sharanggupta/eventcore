# Options (Raw) — eventcore (DIVERGE Phase 3)

**Agent**: Flux (nw-diverger) | **Date**: 2026-07-05
**Rule in force**: generation only. No evaluation, scoring, or ranking appears in this file.

---

## 1. HMW Question

> **How might we give teams certainty that every action their system takes is provable and
> reaches everyone who depends on it?**

Validity check: no embedded solution (a dashboard, replay, or proof is *one* answer, not the
question) | outcome-oriented | positive framing | broad enough for structurally different
answers. Derived from the validated job in `job-analysis.md`.

---

## 2. SCAMPER Options (one per lens)

### Option S: Durable Pull Subscriptions & Replay
**Core idea**: Consumers read the event log at their own pace through named cursors and can rewind to any past position — the log itself becomes the delivery mechanism.
**Key mechanism**: Substitute push delivery with consumer-driven pull: per-consumer named cursors over the existing TimescaleDB log, `GET /v1/subscriptions/{name}/events` returning from the cursor, cursor commit semantics, rewind/replay to any time or event id.
**Key assumption**: Downstream teams prefer controlling their own consumption (pace, position, backfill) over being pushed to; a new consumer must be able to start from history, not from now.
**SCAMPER origin**: Substitute (replace the push mechanism entirely).
**Closest competitor**: AWS EventBridge archive+replay; Kafka consumer-offset model; Svix "Replay Missing".

### Option C: Flow Monitors
**Core idea**: Operators declare what event flow *should* look like ("`invoice.paid` arrives at least daily"; "webhook X succeeds within 5 minutes") and EventCore raises an alert when reality diverges — including when events stop arriving at all.
**Key mechanism**: Combine the event-log job with the monitoring/alerting job: assertion rules evaluated continuously over the ingest stream and delivery outcomes; notifications via outbound channels; a `/metrics` endpoint for scrape-based tooling.
**Key assumption**: The scariest failure mode is silence — an event that never happened, or a flow that quietly stopped — and the log is the natural place to detect absence.
**SCAMPER origin**: Combine (event record + expectation monitoring in one system).
**Closest competitor**: Hookdeck Issues (delivery-side alerting); no competitor monitors ingest absence.

### Option A: Delivery Control Plane
**Core idea**: Every delivery attempt becomes visible and recoverable: per-attempt logs (status, response, timestamps), a dead-letter list of exhausted deliveries, one-call redelivery, and per-subscription event-type filters.
**Key mechanism**: Adapt the delivery-observability surfaces documented across the webhook category (attempt capture, dead-letter listing, manual/bulk retry, endpoint filtering) to EventCore's existing outbox tables — API-first, no UI required.
**Key assumption**: Adopters' dominant pain arrives *after* deployment, when a delivery fails and the operator has no way to see why or make it right.
**SCAMPER origin**: Adapt (borrow the proven operational surface from Convoy/Hookdeck/Svix, transplanted to a first-party self-hosted context).
**Closest competitor**: Convoy (per-attempt capture, batch retry); Hookdeck Issues; Svix Recover Failed.

### Option M: Compliance Vault
**Core idea**: The event record becomes provable rather than merely stored: hash-chained events, verifiable integrity proofs, declared retention policies, and signed exports an auditor can check independently.
**Key mechanism**: Magnify the trust dimension: per-event hash chaining (each event commits to its predecessor), periodic anchor points, a verification endpoint, Timescale `drop_chunks`-backed retention policies that are themselves recorded as events, export bundles with detached signatures.
**Key assumption**: A class of self-hosting buyers adopts an audit log *because* of compliance obligations (SOC 2, ISO 27001, disputes) and needs the record to be demonstrably unaltered, not just present.
**SCAMPER origin**: Modify/Magnify (amplify the "prove what happened" dimension).
**Closest competitor**: immudb (cryptographic proofs, database category); WorkOS Audit Logs (asserted immutability, retention pricing).

### Option P: Webhooks-as-a-Feature
**Core idea**: EventCore becomes the engine a SaaS vendor embeds to offer webhooks *to its own customers*: organizations/applications as first-class tenants, per-tenant endpoints and keys, and a hosted consumer portal for endpoint self-service.
**Key mechanism**: Put the existing pipeline to a different use by adding a tenancy layer (application-per-customer model), tenant-scoped ingest/delivery/keys, and an embeddable status surface.
**Key assumption**: The buyer is a SaaS vendor whose customers demand webhooks; they want Svix-like capability without a hosted third party in their delivery path.
**SCAMPER origin**: Put to other use (same pipeline, different buyer and topology).
**Closest competitor**: Svix (application model, App Portal); Convoy (portal links, customer dashboards).

### Option E: Forensic Search Engine
**Core idea**: EventCore doubles down on answering "what happened?": time-range queries, payload-field search, actor/entity correlation across event types, saved investigations, and export — with webhook delivery de-emphasized to a secondary role.
**Key mechanism**: Eliminate the delivery pipeline as the center of gravity; invest the whole surface in query: JSONB payload indexing (GIN), time-bucket queries on the hypertable, correlation by payload key (e.g. all events touching `userId=u_123`), CSV/NDJSON export.
**Key assumption**: The record's value is realized at investigation time; teams reach for the audit log during incidents and disputes far more often than they add consumers.
**SCAMPER origin**: Eliminate (remove the most complex moving part — delivery — from the product's focus).
**Closest competitor**: WorkOS Audit Logs (structured query, SIEM export); Retraced (searchable self-hosted record).

### Option R: Source Capture
**Core idea**: Instead of trusting application code to remember to POST events, EventCore reaches into the source: it tails the application's database (WAL/outbox tables) and turns row changes into events — nothing that happens can be forgotten.
**Key mechanism**: Reverse who initiates capture: embedded CDC (e.g. Debezium engine) connecting to the team's PostgreSQL, mapping configured tables/outbox rows to EventCore events, which then flow through the existing store-and-deliver pipeline.
**Key assumption**: App-emitted events are structurally incomplete (developers forget, code paths bypass emission, bugs drop events); capture at the database is the only complete record.
**SCAMPER origin**: Reverse (system pulls facts from the source instead of the source pushing).
**Closest competitor**: Debezium + hand-rolled outbox relay (the DIY incumbent).

---

## 3. Crazy 8s Supplements (structurally distinct, 1-minute each)

### Option C8-1: Terminal Ops Companion
**Core idea**: A CLI/TUI — `eventcore tail`, `eventcore search`, `eventcore redeliver` — that makes the running system inspectable and recoverable from the terminal where its operators already live.
**Key mechanism**: Thin client over inspection/recovery APIs (attempt logs, dead letters, redelivery) with live streaming.
**Key assumption**: EventCore's operators are terminal-native and a CLI is the interface they trust.
**SCAMPER origin**: Crazy 8s supplement.
**Closest competitor**: Hookdeck CLI.

### Option C8-2: Signed Event Receipts
**Core idea**: Every ingested event returns a cryptographic receipt the producer can store and later hand to an auditor to prove the event existed, unaltered, at ingestion time.
**Key mechanism**: Per-event signature over (id, time, type, payload hash) chained to the log head; standalone verification tool.
**Key assumption**: Producers need to prove individual events to third parties without granting access to the whole log.
**SCAMPER origin**: Crazy 8s supplement.
**Closest competitor**: immudb client-side verification.

### Option C8-3: Analyst Surface
**Core idea**: A read-only SQL/export path (read replica credentials or DuckDB-friendly Parquet export) so analysts and auditors query the event history with their own tools.
**Key mechanism**: Read-only role + documented schema + periodic columnar export.
**Key assumption**: The people interrogating history are not the people operating the pipeline, and they bring their own tools.
**SCAMPER origin**: Crazy 8s supplement.
**Closest competitor**: WorkOS SIEM/S3 log streams.

### Option C8-4: Single-Binary Edition
**Core idea**: `./eventcore` — one native binary with embedded storage, no Docker, no external database; the five-minute walkthrough becomes a thirty-second one.
**Key mechanism**: GraalVM native image + embedded storage engine; same API surface.
**Key assumption**: Adoption friction (Compose stack, TimescaleDB dependency) is what stops teams from trying EventCore at all.
**SCAMPER origin**: Crazy 8s supplement.
**Closest competitor**: immudb's "runs on your notebook" posture; SQLite-style distribution.

---

## 4. Curation — Merges and Removals (structural, not evaluative)

| Raw option | Disposition | Reason (structural) |
|---|---|---|
| A + C8-1 | **Merged** | Same mechanism (inspection & recovery of the delivery pipeline), same assumption (post-deployment operational blindness), same cost profile (surface over existing outbox data). C8-1 is an interface variation of A. Strongest representative: A, with CLI noted as a possible surface. |
| C + A | **Merged → "Pipeline Control Tower"** | Same core mechanism (observe & act on pipeline health over existing tables), same assumption (the pain is operating blind), same cost profile (rules/endpoints over existing data; no new consumption model). C covers ingest-side silence, A covers delivery-side failure — two halves of one observability approach. |
| M + C8-2 | **Merged** | Same mechanism (cryptographic proof of record integrity), same assumption (provability buyer), same cost profile. C8-2 is the per-event granularity of M's chain. Strongest representative: M, receipts folded in. |
| E + C8-3 | **Merged** | Same mechanism (expanded query surface over the stored record), same assumption (value realized at investigation time), same cost profile. C8-3 is an access-path variation of E. |
| C8-4 | **Removed** | Packaging/distribution concern orthogonal to the job: it changes how any option is delivered, not which job facet is served. Compatible with all six curated options; not a competing direction. Recorded for possible reuse by DESIGN/DELIVER waves. |

## 5. Curated Six

| # | Option | Mechanism | Assumption about user | Cost/effort profile |
|---|--------|-----------|----------------------|---------------------|
| 1 | **Pipeline Control Tower** (A+C+C8-1) | Observability & recovery surface over the existing pipeline: attempt logs, dead letters, redelivery, per-subscription filters, flow monitors, metrics | Pain is operating blind after deployment | Incremental — new endpoints/rules over existing outbox and event tables |
| 2 | **Durable Pull Subscriptions & Replay** (S) | Consumer-driven pull with named cursors and rewind | Consumers want control of pace and position; history must be consumable | Moderate — new consumption model beside push, cursor bookkeeping |
| 3 | **Compliance Vault** (M+C8-2) | Cryptographic hash chain, verification, retention policy, signed exports/receipts | Buyer adopts because record must be provable to third parties | Moderate — write-path chaining, verification jobs, policy engine |
| 4 | **Webhooks-as-a-Feature** (P) | Tenancy layer: applications, tenant-scoped keys/endpoints, consumer portal | Buyer is a SaaS vendor serving *its* customers' webhook demand | Large — authz/data-model rework, portal surface |
| 5 | **Forensic Search Engine** (E+C8-3) | Query/indexing investment: payload search, correlation, time-range, exports | Value is realized at investigation time, not delivery time | Moderate — indexing strategy, query API, storage growth |
| 6 | **Source Capture** (R) | Embedded CDC tailing the team's database into the pipeline | App-emitted events are structurally incomplete | Large — connector lifecycle, schema mapping, new failure domain |

## 6. Diversity Test (3-point, per option pair-wise)

- **Different mechanism?** 1: observe/recover push pipeline · 2: pull consumption · 3: cryptography + lifecycle policy · 4: tenancy/productization · 5: query/indexing · 6: WAL capture — all distinct. PASS
- **Different assumption about user behavior?** 1: operator recovering failures · 2: consumer controlling consumption · 3: compliance buyer proving to auditors · 4: vendor reselling to customers · 5: investigator interrogating history · 6: skeptic distrusting app instrumentation — all distinct. PASS
- **Different cost/effort profile?** Incremental-on-existing (1) vs new consumption model (2) vs crypto/policy engine (3) vs tenancy rework (4) vs index/storage investment (5) vs connector platform (6) — all distinct. PASS

No two options share all three dimensions; no further merges required.

## Gate G3 Evaluation

- [x] HMW framed without embedded solution — PASS
- [x] All 7 SCAMPER lenses applied, one option each — PASS
- [x] Crazy 8s supplements: 4 generated — PASS
- [x] Curated to exactly 6 options — PASS
- [x] Each option passes 3-point diversity test — PASS
- [x] No evaluative language in this file — PASS

**G3: PASS.** Proceeding to Phase 4 (taste evaluation).
