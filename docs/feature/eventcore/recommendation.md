# Recommendation — eventcore (DIVERGE → DISCUSS handoff)

**Agent**: Flux (nw-diverger) | **Date**: 2026-07-05
**Derivation**: ranking follows the weighted matrix in `diverge/taste-evaluation.md` with no
overrides and no post-hoc weight changes. Evidence trail: `diverge/job-analysis.md` →
`diverge/competitive-research.md` → `diverge/options-raw.md` → `diverge/taste-evaluation.md`.

---

## Top 3 Options

### 1. Pipeline Control Tower — Score 4.57

Delivery-attempt logs, dead-letter visibility, one-call redelivery, per-subscription
event-type filters, flow monitors, and a metrics endpoint — an observability and recovery
surface over the pipeline EventCore already runs.

- **Why it scores well**: Hits the three highest-opportunity outcomes (O3 15.0, O1-adjacent O5 12.5, O2-tail 13.0). Perfect progressive disclosure (existing users undisturbed) and speed-as-trust (making reliability visible is the trust product). Feasibility 5: it is a surface over tables that already exist.
- **Core trade-off**: Zero differentiation — this is catch-up. Every competitor already ships it (research patterns P1–P3). It makes EventCore *choosable*, not *unique*.
- **Key risk**: The assumption that adopters are first-party operators who feel post-deployment blindness. If actual adopters are vendors reselling webhooks (Svix's buyer), this under-serves them.
- **Hire criteria**: A team already running EventCore (or evaluating it against Convoy) that has been burned by a silently exhausted delivery and needs to see, explain, and fix failures without SSH-ing into the database.

### 2. Durable Pull Subscriptions & Replay — Score 3.80

Named consumer cursors over the existing append-only log; consumers read at their own pace and
rewind to any point; new consumers backfill from history.

- **Why it scores well**: Leans into the one structural asset no webhook gateway can copy — the durable log (they expire payloads in 3–90 days; EventCore keeps them). Cleanly subsumes O8 (13.0) and the O2 tail. Minimal surface (T1 4): replay falls out of the log for free.
- **Core trade-off**: Introduces a second consumption model beside push (T2 3) — every future feature must answer "for push, for pull, or both?"
- **Key risk**: The assumption that downstream teams will run polling consumers. If consumers are third parties who demand fire-and-forget webhooks, pull adoption stalls.
- **Hire criteria**: A team adding its Nth internal consumer, or one that needs to rebuild a projection/backfill a new service from event history.

### 3. Forensic Search Engine — Score 3.50

Payload-field search, time-range queries, actor/entity correlation, and exports — the record
becomes interrogatable; delivery de-emphasized.

- **Why it scores well**: O1 is the second-highest opportunity (14.0); taste profile is clean (one new concept, progressive growth from today's `?type=` filter).
- **Core trade-off**: Viability 2 — the eliminate-delivery framing abandons the dual-facet white space that research pattern P4 identifies as EventCore's only uncontested position, and walks into entrenched free competition (ELK/Loki/SIEM).
- **Key risk**: The assumption that investigation frequency outweighs delivery dependence. The research suggests the opposite: delivery gaps are what make adopters choose Convoy today.
- **Hire criteria**: A team whose primary use is incident/dispute forensics and whose webhook needs are already minimal.

---

## Recommendation

**Pipeline Control Tower (4.57)** — by a margin of 0.77 over second place, the largest gap in
the matrix. The rationale is fully score-derived: it dominates on the evidence-weighted criteria
(DVF 4.67 — highest desirability and feasibility in the set) *and* on the two heaviest taste
criteria (T3 5, T4 5). The research is unambiguous that dead-letter visibility, recovery, and
per-consumer filtering are the category floor (P1–P3): EventCore's premise — the only
self-hosted product serving both the record and the delivery — is currently unclaimable because
the delivery half is below table stakes. Control Tower closes exactly that gap using data the
system already produces.

**Flagged weakness (explicit)**: Control Tower is defensive — it buys parity, not distinction.
The matrix accepts this because desirability evidence for parity is overwhelming while evidence
for differentiation bets is thinner; DISCUSS should treat "what follows parity" as the next
divergence question. Note also the sequencing asset: attempt records and redelivery mechanics
built here are the operational substrate that Option 2's replay would later reuse. This is an
observation about compatibility, not a hedge on the decision.

## Dissenting Case (for second place)

**Durable Pull Subscriptions & Replay (3.80)** would be the right choice under a different
strategic assumption: that EventCore should *win on what only it can do* rather than reach
category parity. The durable log is the one asset Svix, Hookdeck, and Convoy structurally
cannot match without becoming systems of record; pull-with-replay converts that asset into a
visible product capability (O8, backfill, projection rebuilds) instead of an implementation
detail. If DISCUSS surfaces evidence that adopters are choosing EventCore *because of* the
permanent log (rather than despite the missing delivery ops), or that consumers are
predominantly internal teams comfortable with polling, the 0.77 gap narrows to a judgment call
— and the differentiation argument should win it. The scoring counts against it today because
D-evidence is inference (model proven elsewhere) while Control Tower's D-evidence is universal
competitor behavior.

## Decision for DISCUSS Wave

> **Proceed with Option 1, Pipeline Control Tower** — delivery-attempt logs, dead-letter
> visibility with redelivery, per-subscription event-type filters, flow-stopped detection, and
> a metrics endpoint — **assuming** (a) the primary near-term adopter is a first-party team
> operating its own EventCore instance, not a SaaS vendor reselling webhooks, and (b) reaching
> propagation table stakes precedes any differentiation bet. If DISCUSS invalidates assumption
> (a), re-open this recommendation against the dissenting case rather than proceeding.

Handoff to: **nw-product-owner** (DISCUSS wave), with all supporting artifacts in
`docs/feature/eventcore/diverge/`.
