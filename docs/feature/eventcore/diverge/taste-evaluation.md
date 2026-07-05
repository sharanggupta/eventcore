# Taste Evaluation — eventcore (DIVERGE Phase 4)

**Agent**: Flux (nw-diverger) | **Date**: 2026-07-05
**Inputs**: `job-analysis.md` (job + ODI opportunities), `competitive-research.md` (evidence), `options-raw.md` (curated six)

---

## 1. Weights — LOCKED BEFORE SCORING

EventCore is an API-first infrastructure product operated by developers → **Developer Tool**
weight profile from the taste-evaluation skill, adopted unmodified:

| Criterion | Weight | Rationale |
|-----------|--------|-----------|
| DVF (avg of D, F, V) | 25% | Desirability/feasibility/viability triage carries market evidence |
| T1 Subtraction | 15% | Developer tools tolerate surface if each element earns its place |
| T2 Concept Count | 20% | API concepts are the product's UX; each new noun is adoption cost |
| T3 Progressive Disclosure | 15% | Existing users must be undisturbed; depth on demand |
| T4 Speed-as-Trust | 25% | For delivery infrastructure, perceived reliability/latency IS the product's trust currency |

Weights were fixed before any option was scored. No adjustments were made afterward.

---

## 2. DVF Filter (elimination threshold: total < 6)

Scores 1–5 per lens. Justifications cite research patterns (P1–P5) from `competitive-research.md`
and opportunity scores (O1–O8) from `job-analysis.md`.

| Option | D | F | V | Total | Avg | Verdict |
|--------|---|---|---|-------|-----|---------|
| 1. Pipeline Control Tower | 5 | 5 | 4 | 14 | 4.67 | Survives |
| 2. Pull Subscriptions & Replay | 4 | 4 | 4 | 12 | 4.00 | Survives |
| 3. Compliance Vault | 3 | 4 | 4 | 11 | 3.67 | Survives |
| 4. Webhooks-as-a-Feature | 4 | 2 | 4 | 10 | 3.33 | Survives |
| 5. Forensic Search Engine | 3 | 4 | 2 | 9 | 3.00 | Survives |
| 6. Source Capture | 2 | 3 | 2 | 7 | 2.33 | Survives (near miss) |

**DVF justifications**

1. **Control Tower** — D5: targets the three highest-opportunity outcomes (O3 15.0, O5 12.5, O2-tail 13.0); research P1–P3 show every competitor ships this surface — adopters expect it. F5: pure surface over existing outbox/event tables; attempts already occur, they are just not recorded or exposed. V4: closes the "Convoy is safer to choose" gap (research: Convoy ahead on every propagation outcome); retention driver; table stakes for any future paid tier.
2. **Pull & Replay** — D4: O8 (13.0) and the O2 tail are under-served; EventBridge/Kafka prove consumer demand for the model. F4: the append-only log already exists; cursors are bookkeeping; cost is a second consumption model beside push. V4: leans into the system-of-record wedge no webhook gateway can follow (they expire payloads in 3–90 days).
3. **Compliance Vault** — D3: O4 scored 11.0 (borderline); the buyer exists (WorkOS charges $99/mo/1M events for retention; immudb exists) but we have no direct evidence current EventCore adopters are compliance buyers. F4: hash-chaining a single-writer append log is straightforward; retention via unused `drop_chunks`; anchoring/WORM harder. V4: compliance buyers pay, and research P5 shows product-level tamper evidence is uncontested outside the database category.
4. **Webhooks-as-a-Feature** — D4: Svix/Convoy prove vendor-out demand. F2: multi-tenancy reworks every table, key scope, and authz assumption in a brownfield single-tenant codebase, plus a portal UI — the largest build by far. V4: proven willingness to pay in this segment (Svix Pro $490/mo, Convoy Premium $999/mo).
5. **Forensic Search** — D3: O1 is the second-highest opportunity (14.0) — the want is real. F4: GIN indexes + query API are well-understood. V2: the *eliminate-delivery* framing abandons the dual-facet white space (research P4 — the combination IS the differentiator) and competes with entrenched free tooling (ELK/Loki/SIEM).
6. **Source Capture** — D2: no evidence adopters distrust their own event emission; teams convinced of CDC already use Debezium. F3: embedded Debezium exists, but connector lifecycle inside a single-node Compose product is a new failure domain. V2: head-on with free, dominant Debezium; unclear value capture.

**Result**: no option falls below 6 — all six proceed to taste scoring. The filter still did
work: it demoted Source Capture (7) and Forensic Search (9) materially via the DVF component
of the weighted total, and documented why.

---

## 3. Taste Scores (rubrics from skill, 1–5)

### Option 1: Pipeline Control Tower
- **T1 Subtraction: 4** — each element maps to a documented failure mode (attempt log→O3, dead letters + redelivery→O2 tail, filters→O6, monitors→O5, metrics→O5). Flow monitors could be deferred without breaking core value → not a 5.
- **T2 Concept Count: 4** — one new concept, *delivery attempt / dead letter*, anchored to familiar bounce-queue mental models. Filters reuse the existing event-type noun.
- **T3 Progressive Disclosure: 5** — existing flows unchanged; the new surface appears only when the operator investigates a failure; monitors opt-in.
- **T4 Speed-as-Trust: 5** — listing and redelivery are indexed reads/enqueues with immediate feedback; attempt capture rides the existing delivery transaction; the option's entire purpose is making reliability *visible*, which is the strongest possible trust signal for this product.

### Option 2: Pull Subscriptions & Replay
- **T1: 4** — minimal surface (create subscription, read from cursor, commit); replay falls out of the immutable log for free.
- **T2: 3** — two new concepts: named cursors/commit semantics, and the coexistence of two consumption models the user must choose between.
- **T3: 4** — push users undisturbed; pull adopted on demand; but a new consumer now faces a model choice at first contact.
- **T4: 4** — server reads are fast keyset queries; consumer-side polling introduces perceived lag between event and consumption.

### Option 3: Compliance Vault
- **T1: 3** — chain, verification endpoint, receipts, retention policies, signed exports: several separable elements; chain+verify could stand alone.
- **T2: 3** — two new concepts: tamper-evidence/verification model and retention-policy objects.
- **T3: 4** — invisible until an auditor asks; retention configured once.
- **T4: 3** — hash chaining serializes the write path (chain-head contention) and verification adds background load; noticeable, justified by payoff.

### Option 4: Webhooks-as-a-Feature
- **T1: 2** — tenancy, portal, tenant-scoped keys, app-scoped endpoints: large surface beyond the irreducible job.
- **T2: 2** — three-plus interdependent concepts (organization/application, tenant key scoping, portal roles).
- **T3: 3** — single-tenant path can remain the default; complexity arrives when tenancy is enabled.
- **T4: 4** — delivery mechanics unchanged; portal off the hot path.

### Option 5: Forensic Search Engine
- **T1: 4** — focused query surface; export is the one trimmable element.
- **T2: 4** — one new concept (query filters), anchored to universal search idioms.
- **T3: 4** — today's `?type=` grows into richer parameters progressively.
- **T4: 3** — JSONB payload search at hypertable scale risks visible latency; GIN indexing helps but taxes the write path.

### Option 6: Source Capture
- **T1: 3** — connector config, table mappings, transforms: necessary but not minimal.
- **T2: 2** — replication slots, connectors, table→event mapping approach a new mental model.
- **T3: 2** — DB credentials, slots, and mappings must be configured before any value: front-loaded complexity.
- **T4: 3** — near-real-time capture, but connector lag is opaque to the user.

---

## 4. Weighted Scoring Matrix

Weighted total = DVFavg×0.25 + T1×0.15 + T2×0.20 + T3×0.15 + T4×0.25 (max 5.0)

| Option | DVF avg | T1 Sub | T2 Concept | T3 Prog | T4 Speed | **Weighted Total** | Rank |
|--------|---------|--------|------------|---------|----------|--------------------|------|
| 1. Pipeline Control Tower | 4.67 | 4 | 4 | 5 | 5 | **4.57** | 1 |
| 2. Pull Subscriptions & Replay | 4.00 | 4 | 3 | 4 | 4 | **3.80** | 2 |
| 5. Forensic Search Engine | 3.00 | 4 | 4 | 4 | 3 | **3.50** | 3 |
| 3. Compliance Vault | 3.67 | 3 | 3 | 4 | 3 | **3.32** | 4 |
| 4. Webhooks-as-a-Feature | 3.33 | 2 | 2 | 3 | 4 | **2.98** | 5 |
| 6. Source Capture | 2.33 | 3 | 2 | 2 | 3 | **2.48** | 6 |

Arithmetic spot-check (Option 1): 4.67×0.25=1.168 + 4×0.15=0.600 + 4×0.20=0.800 + 5×0.15=0.750 + 5×0.25=1.250 → **4.57**. ✓

## Gate G4 Evaluation

- [x] DVF filter applied to all 6; eliminations (none) and near-misses documented — PASS
- [x] Weights locked before scoring, documented with rationale, unmodified after — PASS
- [x] All 6 survivors scored on all 4 taste criteria with rubric justifications — PASS
- [x] Weighted ranking complete and arithmetically checked — PASS
- [x] Recommendation (see `../recommendation.md`) traceable to this matrix, dissent included — PASS

**G4: PASS.**
