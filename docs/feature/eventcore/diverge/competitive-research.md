# Competitive Research — eventcore (DIVERGE Phase 2)

**Agent**: Flux (nw-diverger), research executed by nw-researcher | **Date**: 2026-07-05
**Depth**: comprehensive (6 direct products + 1 non-obvious alternative + DIY incumbent)

**Method note**: All product claims are traceable to official docs/pricing/README pages fetched
2026-07-05 (source list at end). Unverifiable claims are marked **[unverified]**. Research maps
each product to the validated job (see `job-analysis.md`), not to feature checklists.

**The validated job**: durably record system actions AND dependably convey them to every
dependent party, without the team building event infrastructure itself. Facets: **forensic**
(retrieve/verify what happened) and **propagation** (convey to downstream consumers).

---

## 1. Svix — hosted webhooks-as-a-service (+ MIT open-source server)

- **What**: Webhook-sending infrastructure for SaaS vendors; hosted service + open-source Rust server. (docs.svix.com/overview, github.com/svix/svix-webhooks)
- **Facets**: Propagation almost exclusively. Forensic only as a 30–90 day ops buffer, not an audit trail.
- **Does well**: Retries with exponential backoff over ~28 hours; "Recover Failed" (retry all failures since a date) and "Replay Missing" (send never-attempted messages) — strong O8; operational webhook `message.attempt.exhausted` on exhaustion + endpoint auto-disable after 5 days of failure (partial O5); per-endpoint event-type filtering (O6); application-per-customer tenant model with self-service App Portal.
- **Fails the job**: No forensic facet (30 days free / 90 days Pro retention; Pro from $490/mo). No immutability proof, no audit query semantics. Self-hosted server explicitly lags the hosted product ("some of the features... are not yet available in this repo" — GitHub README).
- **Key assumption**: Buyer is a SaaS vendor sending webhooks *to its own customers* — not a team recording and consuming its own system's events.
- **ODI coverage**: Strong O2, O6, O8; good O3; partial O5; weak O7; poor O1; absent O4.

## 2. Hookdeck — webhook/event gateway (cloud-only)

- **What**: Hosted "Event Gateway" that queues, filters, transforms, and delivers events. (hookdeck.com/docs/introduction)
- **Facets**: Propagation-first, with the best delivery-failure forensics in the set — but only within short retention.
- **Does well**: Queued delivery via connections (source → destination); configurable retry rules per connection (linear or exponential, up to 50 automatic retries), manual/bulk retry at any time within retention, retries included in event pricing; content-based filtering and transformations (O6); **Issues** — auto-opened on delivery failures with occurrence histograms, first/last seen, alerting to Email/Slack/PagerDuty/OpsGenie/incident.io etc., and bulk replay of affected events after a fix (standout O3/O5); CLI forwarding to local dev servers.
- **Fails the job**: No self-hosting. Forensic retention 3 days (free) / 7 (Team $39/mo) / 30 (Growth $499/mo) — unusable for audit/dispute. No tamper evidence.
- **Key assumption**: Events are ephemeral operational traffic; the archive of record lives elsewhere; third-party cloud in the delivery path is acceptable.
- **ODI coverage**: Strong O2, O3, O5, O6; good O8 (within retention); poor O1/O7; absent O4.

## 3. Convoy — self-hosted webhook gateway (closest structural comparator)

- **What**: "Open-source, high-performance webhooks gateway" in Go on PostgreSQL + Redis. (github.com/frain-dev/convoy, getconvoy.io/docs)
- **Facets**: Propagation, self-hosted — EventCore's most direct rival. Forensic facet thin.
- **Does well**: Best-in-class delivery forensics among webhook products — "For every attempt Convoy captures the request and response headers and payload, the HTTP status and any error, the source IP... and requested at / responded at timestamps" (getconvoy.io/docs/product-manual/events-and-event-deliveries) — direct O3 hit; two retry algorithms (constant, exponential with jitter) + manual Retry, **Force Retry** (re-send even successful deliveries), and batch retries (O2/O8); event routing to multiple endpoints by event type or payload structure (O6, richer than type-only); explicit failure states (Retry/Failed/Discarded); endpoint disabling after consecutive failures with email/Slack notifications (O3/O5); customer-facing embeddable dashboards and "Portal links"; per-endpoint rate limiting, circuit breaking, static IPs, independently scalable workers.
- **Fails the job**: **Elastic License v2.0** — source-available, not OSI open source (matters to permissive-license self-hosters); transformations/RBAC/white-labeling gated behind $999/mo Premium. It is a delivery gateway, not a system of record: no immutability, no documented long-term retention/compliance posture (Cloud tier retention unpublished **[unverified]**).
- **Key assumption**: The user already has (or doesn't need) a system of record; Convoy owns the last mile, typically on behalf of a vendor serving customers.
- **ODI coverage**: Strong O2, O6; good O3, O5, O8; weak O1, O7 **[unverified]**; absent O4.

## 4. WorkOS Audit Logs — the forensic facet as SaaS

- **What**: Hosted audit-log API for B2B SaaS vendors with SIEM streaming and an enterprise admin portal. (workos.com/docs/audit-logs)
- **Facets**: Forensic only. Docs state audit logs "exist as a paper trail," not for active delivery.
- **Does well**: Structured schema (Action/Actor/Targets/Context) that makes incident queries meaningful (O1) — a semantic model EventCore's arbitrary JSON lacks; SIEM streaming to seven destinations (Datadog, Splunk HEC, S3, GCS, Sentinel, Snowflake, generic HTTPS) with stream health states; self-service Admin Portal configuration and CSV export.
- **Fails the job**: The propagation facet is absent — log streams export to SIEMs, not to dependent systems. Not self-hosted. Pricing scales with exactly what an audit log accumulates ("$125/mo" per SIEM connection, "$99/mo per 1M events" retention). No cryptographic tamper evidence documented — O4 rests on trusting WorkOS. Default retention **[unverified]**.
- **Key assumption**: Buyer is a B2B SaaS vendor reselling audit visibility to its enterprise customers — again not a first-party team proving its own behavior.
- **ODI coverage**: Strong O1; good O7, O5 (via SIEM); partial O4 (trust-based); absent O2, O3, O6, O8.
- **Retraced lineage** (github.com/retracedhq/retraced): Apache 2.0, self-hosted (Docker Compose/K8s), "searchable, exportable record of read/write events," embeddable viewer — proof the self-hosted forensic niche exists. Caution: latest release v1.13.1 dated November 2024 (~20 months before access) — maintenance vitality questionable.

## 5. AWS EventBridge — heavyweight propagation alternative

- **What**: AWS serverless event bus with rules, archives, replay, and HTTPS delivery via API destinations. (docs.aws.amazon.com/eventbridge)
- **Does well**: Default retry "for 24 hours and up to 185 times with exponential back off and jitter"; DLQ for exhausted events (O2/O3); **archive + replay** — archives store events indefinitely by default with optional retention (O7 knob), replay by time range with `replay-name` tagging to prevent loops (best-in-class O8); event-pattern filtering per rule (O6 at unmatched sophistication); managed auth for external HTTPS endpoints.
- **Fails the job — specifically the "without building infrastructure myself" clause**: assembly burden is documented in AWS's own pages: per-endpoint connection + service-linked role + secret; 5-second client execution timeout; mTLS unsupported; ~30 headers stripped; misconfigured invocation rates can silently exhaust the 24h retry window without a DLQ; replay ordering only per-minute batches. Archives are replay-only, not queryable (O1 requires shipping events elsewhere). No HMAC-style payload signing found **[unverified]**. AWS lock-in; not self-hosted.
- **Key assumption**: User is already deep in AWS, has ops capacity to compose 5+ primitives per delivery path, and handles forensics separately (CloudWatch/S3).
- **ODI coverage**: Strong O2, O6, O8; good O7, O3; O5 needs CloudWatch **[unverified]**; poor O1; absent O4.

## 6. Non-obvious alternative: immudb — immutable database with cryptographic proofs

- **What**: "A database with built-in cryptographic proof and verification" — Merkle-tree commit log, tamper-evident, client-side verification. (github.com/codenotary/immudb)
- **Why it competes for the same job from a different category**: a team needing to "prove what happened" can write actions to immudb and get what no other product here offers — **O4 via mathematics instead of trust**: "the integrity of the history will be protected by the clients, without the need to trust the database"; records can be versioned but "never change or delete records." Fully self-hostable, embeddable ("on an IoT device, your notebook, a server, on-premise or in the cloud").
- **Fails the job**: Zero propagation — no webhooks, retries, subscriptions, or delivery observability; the team builds all of that itself, violating the job's final clause. License: current versions are BUSL 1.1 (Apache 2.0 after four years) with production non-compete restrictions.
- **Key assumption**: The user's core anxiety is *tampering*; they will adopt a specialized datastore and verifying clients; conveyance is someone else's problem.
- **ODI coverage**: Uniquely strong O4; partial O1; O7 *hindered* (no deletion complicates erasure obligations); absent O2, O3, O5, O6, O8.

## 6b. The DIY null alternative — transactional outbox + relay (the actual incumbent)

The pattern EventCore productizes is what teams hand-build: an outbox table written in the same
transaction, relayed by polling or CDC (Debezium). Guarantees "messages are guaranteed to be
sent if and only if the database transaction commits" but is "potentially error prone" and
yields duplicates requiring idempotent consumers (microservices.io/patterns/data/transactional-outbox).
It competes because it is the default: every team's first answer to this job is "we'll just add
an outbox table." **EventCore's real pitch is against this, not against Svix.**

---

## Market Patterns (cross-cutting, evidence-grounded)

1. **Retries + manual replay are propagation table stakes (O2/O8).** All four propagation
   products ship automatic retries *and* operator-initiated recovery (Svix Recover/Replay,
   Hookdeck issue-scoped replay, Convoy batch retries, EventBridge archive replay). EventCore's
   fixed 5-retry policy with no redelivery is below the floor everywhere.
2. **Per-consumer filtering is universal (O6).** Svix event-type subscriptions, Hookdeck content
   filters, Convoy payload routing, EventBridge event patterns. No shipping competitor delivers
   everything to everyone; EventCore does.
3. **Failure observability is a product surface, not a log line (O3/O5).** Convoy logs full
   request/response/status/IP/timestamps per attempt; Hookdeck ships Issues + request inspection;
   Svix fires operational webhooks and auto-disables dead endpoints; EventBridge has DLQs. Every
   vendor makes "why did delivery fail / has flow stopped" answerable in-product. EventCore's
   lack of dead-letter visibility is a visible anomaly in this category, not a nice-to-have.
4. **Nobody serves both facets of the validated job.** Propagation vendors treat payloads as
   ephemeral (Hookdeck 3–30 days, Svix 30–90 days); forensic vendors (WorkOS, immudb) deliver
   nothing to downstream consumers. A durable, queryable system of record that *also* dependably
   conveys events is white space — precisely the job as stated.
5. **The tenant assumption skews vendor-out, not first-party.** Svix, Convoy, and WorkOS all
   assume you are a vendor exposing events *to your customers*. EventCore's job is first-party
   self-evidence — a different buyer, and a positioning axis no one owns. Corollary: self-hosted
   with truly permissive licensing is scarce (Convoy ELv2, immudb BUSL, Svix MIT but lagging).

## Where EventCore Stands Today

EventCore already occupies the white space no competitor holds — one self-hosted system covering
both durable record and signed delivery — but it under-delivers on both facets relative to
specialists: it misses propagation table stakes every rival ships (O6 filtering, O8 replay, O3
dead-letter visibility, O5 flow detection) and forensic table stakes owned by WorkOS/immudb (O7
retention, O4 tamper evidence, O1 rich query). Its defensible wedge is the first-party,
self-hosted framing with one `docker compose up`. The nearest existential threats are Convoy
(self-hosted, ahead on every propagation outcome, but Elastic-licensed and forensically shallow)
and the DIY outbox (free, familiar, the actual incumbent).

## Gate G2 Evaluation

- [x] 3+ real products named: 6 direct (Svix, Hookdeck, Convoy, WorkOS Audit Logs, AWS EventBridge) + DIY incumbent — PASS
- [x] Non-obvious alternative (different category, same job): immudb (database category serving the forensic facet) — PASS
- [x] No generic market claims: every pattern traceable to per-product evidence — PASS
- [x] Key assumptions documented per competitor — PASS

**G2: PASS.** Proceeding to Phase 3 (brainstorming).

## Sources (all accessed 2026-07-05)

1. https://docs.svix.com/retries · 2. https://docs.svix.com/event-types · 3. https://docs.svix.com/overview · 4. https://www.svix.com/pricing/ · 5. https://github.com/svix/svix-webhooks · 6. https://hookdeck.com/docs/introduction · 7. https://hookdeck.com/docs/issues · 8. https://hookdeck.com/pricing · 9. https://github.com/frain-dev/convoy · 10. https://www.getconvoy.io/docs · 11. https://workos.com/docs/audit-logs · 12. https://workos.com/docs/audit-logs/log-streams · 13. https://workos.com/pricing · 14. https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-archive.html · 15. https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-rule-retry-policy.html · 16. https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-api-destinations.html · 17. https://github.com/codenotary/immudb · 18. https://microservices.io/patterns/data/transactional-outbox.html

Additional sources from the parallel research pass (all accessed 2026-07-05): https://hookdeck.com/docs/retries · https://getconvoy.io/docs/product-manual/events-and-event-deliveries · https://www.getconvoy.io/pricing · https://aws.amazon.com/eventbridge/pricing/ · https://github.com/retracedhq/retraced

**Knowledge gaps**: Convoy Cloud per-tier retention (unpublished); WorkOS default retention and
CSV export configurability; EventBridge payload signing and native flow-stopped alerting;
Hookdeck pause/resume; Debezium outbox docs (403 x2, pattern sourced from microservices.io).
