# Job Analysis — eventcore (DIVERGE Phase 1)

**Agent**: Flux (nw-diverger) | **Date**: 2026-07-05 | **Work type**: brownfield (whole-product evaluation)

> **Evidence honesty note**: No live customer interviews were possible in this session.
> This analysis is grounded in (a) the product's actual, verified behavior (README walkthrough,
> API surface, delivery semantics) and (b) desk research. All Importance/Satisfaction figures
> below are **desk-research proxy estimates**, not surveyed values. They must be validated in a
> future DISCOVER pass before high-stakes bets are placed on them.

---

## 1. Raw Request (verbatim)

> "Brownfield: existing product, evaluating the whole product for feature gaps and market fit.
> Where should EventCore go next?"

This is a solution-space question ("which features?"). Per methodology, the job is extracted
first; options are brainstormed against the job, not against the feature-gap list.

## 2. Job Extraction — 5 Whys from the Product's Observable Behavior

**Visible activity**: Teams POST application events to EventCore over HTTP; EventCore stores
them in TimescaleDB and fans them out to registered webhooks with HMAC signatures and retries.

1. **Why do teams send events to EventCore?** To keep a durable record of what their system did,
   and to notify other systems that it happened.
2. **Why do they need a durable record?** To answer "what happened, when, and in what order"
   during incidents, customer disputes, and audits — after the fact, when application state
   alone no longer tells the story.
3. **Why do they need to notify other systems through a third party?** Because hand-rolled
   delivery (fire-and-forget HTTP calls from app code) loses events silently: no outbox, no
   retries, no signatures. Every team rebuilds this badly.
4. **Why is a lost or unprovable event expensive?** Because customer trust, revenue events
   (invoices, orders), and compliance obligations all depend on an accurate, provable account
   of what the system did — and on dependent systems staying consistent with that account.
5. **Why does that matter strategically?** The business must be able to *prove its own behavior*
   and *keep its ecosystem consistent with that behavior* — without diverting engineers into
   building event infrastructure.

Further "why?" produces life-goal answers ("run a trustworthy business") → stop.

**Abstraction level check**:
- Tactical: "add search / add a dashboard" — rejected as job.
- Operational: "operate an event log and webhook fan-out" — rejected: nobody wakes up wanting
  to operate an event log.
- **Strategic (selected)**: reduce uncertainty about what the system did and whether dependents
  know about it.
- Physical (irreducible function): **capture fact → preserve fact → retrieve/verify fact →
  propagate fact**. Any solution to this job must perform these four functions, whatever the
  technology.

## 3. Job Statements

**Functional (primary, strategic level)**:
> When my software system performs actions that customers, auditors, or dependent systems rely
> on, I want each action durably recorded and conveyed to every party that depends on it, so I
> can prove what happened and keep dependents consistent — without building event
> infrastructure myself.

The job has two inseparable facets the product already bundles:
- **Forensic facet**: retrieve and verify the account of what happened (audit, incident, dispute).
- **Propagation facet**: convey what happened to dependents, dependably (integrations, webhooks).

**Emotional**:
> Feel calm during an incident or audit — certain the record is complete, and certain that
> nothing was silently dropped on the way to a dependent system.

**Social**:
> Be seen by auditors, customers, and integration partners as an operation whose word about its
> own behavior can be trusted.

## 4. Disruption Check

Is there a higher-level job that would make this job unnecessary?

- **Platform absorption**: cloud audit trails (AWS CloudTrail, GCP Audit Logs) prove *infrastructure*
  behavior, not *application/business* behavior (`invoice.paid`, `user.signed_up`). They do not
  absorb this job.
- **Streaming platforms** (Kafka et al.) serve the propagation facet at much higher operational
  cost and do not serve the forensic facet without significant assembly. They compete for large
  teams, not for the "without building infrastructure myself" clause.
- **Conclusion**: the job survives the disruption check. The clause most at risk of absorption
  is propagation (hosted webhook services); the forensic facet is stickier for self-hosters.

## 5. ODI Outcome Statements

Format: Direction + Metric + Object + Context. No solution references, no compounds.

| # | Outcome statement |
|---|---|
| O1 | Minimize the time it takes to locate the events related to a specific incident |
| O2 | Minimize the likelihood of a downstream system missing an event that occurred |
| O3 | Minimize the time it takes to determine why an event failed to reach a downstream system |
| O4 | Minimize the effort required to demonstrate that the event record has not been altered |
| O5 | Minimize the time it takes to detect that event flow from a producing system has stopped |
| O6 | Minimize the number of events a downstream system receives that it did not ask for |
| O7 | Minimize the effort required to keep stored events within a retention policy |
| O8 | Minimize the time it takes to bring a new downstream consumer up to date with events that occurred before it existed |

## 6. Opportunity Scoring (desk-research proxies — see honesty note)

Satisfaction = how well **current EventCore** serves each outcome, derived from verified product
behavior. Importance = estimated from the job analysis and competitor emphasis (validated in
Phase 2). Formula: `Score = Importance + max(0, Importance − Satisfaction)`.

| # | Outcome | Imp (est) | Sat (current product, evidence) | Score | Status |
|---|---------|-----------|-------------------------------|-------|--------|
| O3 | Determine why delivery failed | 8.5 | 2.0 — after 5 failed attempts a delivery is abandoned; no API exposes attempts, failures, or dead letters | 15.0 | **Under-served** |
| O1 | Locate events for an incident | 8.5 | 3.0 — only `?type=` filter; no time-range, payload, or correlation search | 14.0 | **Under-served** |
| O2 | Downstream never misses an event | 9.0 | 5.0 — transactional outbox + 5 signed retries is genuinely strong *until* attempt 6, when the event is silently lost to that consumer forever | 13.0 | **Under-served** |
| O8 | Bring new consumer up to date | 7.5 | 2.0 — no replay or redelivery of any kind | 13.0 | **Under-served** |
| O5 | Detect event flow stopped | 7.5 | 2.5 — `/health` liveness only; no metrics, no absence detection | 12.5 | **Under-served** |
| O4 | Demonstrate record unaltered | 7.0 | 3.0 — soft-deleted keys preserve audit trail, but a DB admin can mutate the events table undetected | 11.0 | Appropriately served (borderline) |
| O6 | No unwanted events delivered | 6.5 | 2.0 — every webhook receives every event; no per-subscription type filter | 11.0 | Appropriately served (borderline) |
| O7 | Retention within policy | 6.0 | 2.5 — Timescale `drop_chunks` available but unused; unbounded growth | 9.5 | Lower priority |

## 7. Opportunity Candidates

The most under-served outcomes cluster around two themes:

1. **Operating the pipeline blind** (O3, O5, O2-tail): once EventCore is deployed, its operator
   cannot see failures, cannot recover them, and cannot tell when flow stops.
2. **After-the-fact access** (O1, O8): the record exists but is hard to interrogate and
   impossible to re-propagate.

## Gate G1 Evaluation

- [x] Job at strategic level (with physical-level irreducible function documented) — PASS
- [x] No feature references in job statement — PASS
- [x] ODI outcome statements: 8 produced (minimum 3) — PASS
- [x] Functional + emotional + social statements — PASS
- [x] Disruption check performed — PASS

**G1: PASS.** Proceeding to Phase 2 (competitive research).
