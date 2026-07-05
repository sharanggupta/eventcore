# EventCore Market Positioning — Flat-Price Managed, Open-Source-Always

**Date**: 2026-07-05 | **Researcher**: nw-researcher (Nova) | **Confidence**: High (pricing — official pages), Medium (community sentiment, Convoy status) | **Sources**: 22
**Extends**: `docs/feature/eventcore/diverge/competitive-research.md` (job/feature lens) with a commercial/pricing lens.

**Business model under evaluation**: Open source, always; self-hosted always free and full-featured (no open-core gating). The only paid offering is a managed single-tenant deployment on a Hetzner VM (~£20/mo infra cost), flat-priced £29–79/mo, unlimited events, EU-hosted, customer owns the data.

## Executive Summary

At the reference workload (1M events/month, 5 endpoints), every credible managed competitor charges $500–1,200/month — Svix Pro ≈$585, Hookdeck Growth ≈$649, Convoy self-hosted Premium $999 (license alone), WorkOS ≈$1,188 at steady-state 12-month retention — because all of them meter by event, delivery, or retained volume. The only sub-$200 product option (Hookdeck Team, ≈$189) carries 7-day retention, which fails EventCore's forensic facet outright. Against this, a flat £29–79/month single-tenant instance on a €6.80–25/month Hetzner VM undercuts the field 5–15× with 60–90% gross margin, and the discount *grows* with customer volume — inverting the industry's incentive structure.

The model is structurally protected: Convoy (ELv2) and Hook0 (SSPL) are legally barred from being offered as managed services by third parties and cannot match a no-open-core pledge without relicensing; Svix's MIT server openly lags its cloud; Hookdeck has no self-host at all. The database is not the constraint — single-node TimescaleDB sustains ~100K–1.2M rows/s in vendor and adversarial benchmarks, five orders of magnitude above the target ingest rate; the real ceilings are disk/retention, ops automation debt, and single-node availability. What blocks taking money today is not features but operations: backup/restore/upgrade automation (the Elestio baseline at $17/mo), an honest 99.9% SLA with a status page, an operational data-exit button, and GDPR paperwork — three of the top four gaps are S-effort. The principal risks are self-cannibalization by generic OSS hosts, Hetzner cost drift (prices rose June 2026), and support economics at the low tier.

## 1. Competitor Pricing (cited)

All pricing fetched from official pricing pages on 2026-07-05. Billing-unit definitions differ per vendor and materially change the bill — noted per row.

| Vendor | Free tier cap | Paid tiers | Per-unit cost | Retention | Billing unit definition |
|---|---|---|---|---|---|
| **Svix** | 50k msgs/mo, 30-day retention, 200 msg/s | Pro **$490/mo** (50k msgs incl.); Enterprise custom | **$0.0001/message** overage | 30d free / 90d Pro | Each 64 KiB payload chunk = 1 message; "only attempted messages are counted... retries are both free" [1] |
| **Hookdeck** | 10k events/mo, 3-day retention, dashboard locks on overage | Team **from $39/mo**; Growth **from $499/mo** (SLA 99.999%, SSO/SAML, 30d retention); Enterprise custom | **$3.00 per 100k events** (down to $0.35/100k above 500M/mo) | 3d / 7d / 30d | "A request can result in 0, 1 or multiple events" — each delivery to a destination is an event; retries included [2] |
| **Convoy** | Self-hosted Community: $0 forever (feature-gated, see §2) | Self-hosted Premium **$999/mo**; Enterprise custom; **Cloud pricing unpublished** | n/a (license, not usage) | "Advanced webhook retention" is Premium-gated | License gates features, not volume [3] |
| **WorkOS Audit Logs** | None — all usage billable | Usage-based only | **$99/mo per 1M events retained** + **$125/mo per SIEM log stream** | You pay for what you keep — cost accumulates with retention | Priced on events *retained*, not ingested: an audit log's cost grows forever by design [4] |
| **Inngest** | 50k executions/mo, 500k events, 24h trace retention | Pro **from $99/mo** (1M executions, 5M events incl.); Enterprise custom | $50 per additional 1M executions; $0.50 per additional 1M events ingested | 24h / 7d / 90d traces | "An execution is a single durable function run plus each step inside it" [5] |
| **Trigger.dev** | $5/mo free credits, 1-day logs | Hobby $10/mo; Pro **$50/mo** + metered compute | $0.000025/run invocation + $0.0000169–0.00068/sec compute | 1d / 7d / 30d logs | Self-hosting is Apache 2.0, free, full-featured [6] |
| **AWS EventBridge** | AWS-service events free | Pure usage | **$1.00/M** custom events published; **$0.20/M** API-destination (HTTPS) invocations; archive $0.10/GB processing + $0.023/GB-mo storage; replay $1.00/M | Archive: indefinite (you pay storage) | Per-event + per-delivery + per-GB; no flat option [7] |
| **Webhook Relay** (tunnel/relay tools) | see §1b | see §1b | see §1b | — | Relay/tunnel category, not a system of record |

### 1b. What 1M events/month + 5 endpoints costs (steady state)

Scenario: 1M events ingested per month, each fanned out to 5 endpoints (5M delivery attempts/mo where the vendor bills per delivery).

| Vendor | Monthly cost | Working |
|---|---|---|
| Svix Pro | **≈ $585/mo** (≈ $985 if messages are counted per endpoint — fan-out billing ambiguous on pricing page [unverified]) | $490 + (1M − 50k) × $0.0001 = $490 + $95 [1] |
| Hookdeck Team | **≈ $189/mo** (only 7-day retention) | $39 + 4.99M/100k × $3.00 ≈ $39 + $150; deliveries are events [2] |
| Hookdeck Growth | **≈ $649/mo** (30-day retention, SLA, SSO) | $499 + ≈$150 overage [2] |
| Convoy self-hosted Premium | **$999/mo** license + your own infra | Flat license; Community $0 but feature-gated [3] |
| WorkOS Audit Logs | **$99/mo per 1M retained — ≈ $1,188/mo at steady state with 12-month retention** (+$125/SIEM stream) | 1M/mo ingest × 12mo retained = 12M × $99/M [4] |
| Inngest Pro | **≈ $99–299/mo** (category mismatch: durable functions, not webhook delivery; 5M sends ≈ 5M executions → $99 + 4M × $50/M) | [5] |
| AWS EventBridge | **≈ $3/mo** in AWS fees ($1 publish + $1 API-destination invocations + ≈$1 archive) — the real cost is the documented assembly burden (per-endpoint connection + role + secret, 5s timeout, no mTLS, header stripping; see diverge research §5) | [7] |

**Reading**: at 1M events/mo the market clusters at **$500–1,200/mo** for a product that does the job (Svix Pro, Hookdeck Growth, Convoy Premium, WorkOS at steady-state retention). Hookdeck Team at ~$189 is the cheapest credible product price but carries 7-day retention — useless for the forensic facet. EventCore managed at **£29–79 flat (~$37–100)** undercuts every credible option by 5–15× at this volume, and the gap *widens* as volume grows because every competitor meters.

## 2. Open-Core Monetization and Community Friction

How each "open" competitor actually gates value — and where the friction shows:

**Convoy (Elastic License v2.0 — source-available, not OSI open source).** The free self-hosted Community tier excludes: "Webhook transformation with JS", "Role-based access control", "Advanced webhook retention", "Endpoint circuit breaking", white-labelling, OpenTelemetry export, Google OAuth — all gated behind the **$999/mo Premium** self-hosted license; SAML SSO and SLA are Enterprise-only [3]. ELv2 also legally blocks anyone from offering Convoy as a managed service. Retention — the forensic facet — is explicitly a paid feature.

**Svix (MIT server, open-core in practice).** The open-source server "explicitly lags the hosted product ('some of the features... are not yet available in this repo' — GitHub README)" [8, prior diverge research]. Hook0's comparison (competitor-sourced, bias noted) states the same: Svix is "open-core" and "the open-source version lacks features available in the paid tier" [9]. Community friction on Svix is about price cliff, not license: there is "no mid-tier option between free and $490/mo" [10]; a Hookdeck-published case study (bias noted) claims a customer's "monthly bill went from ~$600 on Svix to $10" after switching [10].

**Hookdeck.** No self-hosting at all — "proprietary with no open-source versions available for self-hosting" [9]. (Its newer OSS sending library "Outpost" exists but is the sending SDK/infra, not the gateway product [10].)

**Hook0 (SSPL — source-available).** The closest philosophical precedent: an EU bootstrapped vendor claiming "the self-hosted and cloud versions have the same features" and "zero per-message costs, forever" when self-hosting [9]. But SSPL is not OSI-approved, destinations are HTTPS-only, and it is positioned as unsuitable "beyond a few thousand events per day" (per a Svix competitor page — bias noted, [11]).

**Conclusion for EventCore**: the "truly OSI-licensed + full-featured self-host + flat managed price" cell of the matrix is **empty**. Convoy gates features, Svix gates the hosted delta, Hookdeck gates everything, Hook0 gates the license. EventCore's no-open-core pledge is verifiable and differentiating — and ELv2/SSPL competitors *cannot* copy the model without relicensing.

### Conflicting Information: Is Convoy still alive?
**Position A**: Svix's alternatives page (competitor marketing, low trust for this claim) asserts the Convoy company "is no longer active", the project is "a side project", with "measured uptime below 99.0% over the last 12 months" [11].
**Position B**: Convoy's GitHub repository shows release **v26.6.0 dated 27 June 2026** — eight days before this research — with 195+ releases total [12].
**Assessment**: Primary evidence (GitHub releases) beats competitor marketing: the *project* is actively shipping. The *company's* commercial vitality remains unverified — both can be true. Treat Convoy as an active technical competitor whose commercial roadmap is uncertain. Confidence: Medium.

## 3. The Flat-Price Positioning Statement

> **EventCore is the event audit log and webhook delivery system you can actually own.** Open source forever — the self-hosted version is the whole product, no open-core asterisks. If you'd rather not run it, we run your private instance on EU soil (Hetzner, GDPR, ISO 27001 data centres) for one flat coffee-budget price — £29–79/month, unlimited events, unlimited endpoints, your data exportable any day. Competitors meter your success: at 1M events/month you'd pay ~$585 on Svix, ~$649 on Hookdeck Growth, $999 for Convoy's self-host license, or ~$1,188/month on WorkOS once a year of audit logs accumulates. We charge the same whether you send ten events or ten million — because a single Hetzner VM handles both, and we'd rather you grow.

Three load-bearing claims, each evidenced:
1. **"Usage-based pricing punishes success"** — WorkOS prices per events *retained* ($99/mo per 1M), so an audit log's bill grows forever by design [4]; Svix meters every message past 50k [1]; Hookdeck meters every delivery [2].
2. **"No open-core asterisks"** — every "open" competitor gates something (§2). EventCore's pledge is checkable in the repo.
3. **"EU data residency by default"** — Hetzner: GDPR-compliant, German/Finnish data centres, ISO/IEC 27001, 99.9% uptime SLA [13]. Svix, Hookdeck, WorkOS are US-headquartered SaaS; for EU buyers post-Schrems II this is a procurement shortcut, not a nicety. *(Interpretation, clearly labelled: residency alone rarely wins a deal, but it removes a blocker.)*

## 4. Commercial Gap Analysis — What EventCore Needs at £29–79 Flat

Baseline: EventCore has already shipped type filtering, payload signing, retries, dead-letter ops, single+bulk redelivery, pipeline metrics, flow-stopped signal, and API keys (see `docs/feature/eventcore/slices/`). The gaps below are what stands between that and *taking money*. Ranked by how directly each blocks a purchase decision.

| # | Gap | Why it blocks revenue (evidence) | Effort |
|---|-----|----------------------------------|--------|
| 1 | **Managed-ops automation: provisioning, monitoring, automated backups + tested restore, managed upgrades** | At £29–79 the *product being sold is operations*, not software. The generic-hosting baseline already includes this: Elestio bundles "SSL, backups, and auto-updates" on a dedicated single-tenant VM for ~$17/mo [14]. If EventCore-managed does less than Elestio-generic, there is no offer. | **L** (full automation) — but **M** to launch: manual runbook + Ansible is fine below ~20 customers |
| 2 | **Published SLA, status page, and a stated support channel** | Every paid competitor sells certainty: Hookdeck Growth advertises "99.999% uptime SLA" [2]; Convoy Enterprise sells "Dedicated support & SLA" [3]; Hetzner's own infra gives 99.9% [13]. EventCore cannot honestly promise five 9s on one VM — promise 99.9% with a public status page and email/Slack support and say so plainly. | **S** |
| 3 | **Operational data-exit path: scheduled off-VM backups + documented one-command export/import to self-host** | "You own the data" is the positioning's spine; it must be a button, not a promise. Contrast sells itself: WorkOS charges $99/mo per 1M events *retained* — leaving means abandoning or re-paying for your history [4]. Migration in/out is also the credible answer to "what if you disappear?" (the #1 small-vendor objection). | **S–M** |
| 4 | **GDPR paperwork: DPA template, subprocessor list (Hetzner), retention controls** | The EU-residency wedge is only usable in procurement if a DPA exists. Hetzner supplies the substrate (GDPR, ISO 27001 [13]); EventCore must supply the paper. Retention/erasure controls also answer the audit-log GDPR tension flagged for immudb in the diverge research. | **S** |
| 5 | **Content-based filtering / payload transformations** | The one *product* feature every paying competitor has beyond EventCore's shipped set: Hookdeck content filters + transformations [2], Convoy payload routing + JS transformations ($999-gated! [3]), Svix transformations "included in all plans" [1]. Shipping this free-and-open directly devalues Convoy's Premium gate. | **M** |
| 6 | **Per-endpoint rate limiting + circuit breaking** | Convoy gates "Endpoint circuit breaking" behind $999/mo Premium [3]; Svix auto-disables failing endpoints [prior research]. Needed for one VM to protect itself from one slow consumer — an ops-margin feature as much as a product feature. | **M** |
| 7 | **Dashboard SSO (OIDC first, SAML later)** | Gated to the ~$499+ tier everywhere: Hookdeck Growth "SSO/SAML/SCIM" [2], Convoy Enterprise "SAML SSO" [3], Trigger.dev Enterprise SSO [6], Webhook Relay Enterprise "SAML SSO" [15]. Not a blocker at the £29–79 SMB price point — but including plain OIDC flat would be another "they gate it, we don't" proof point. | **M** |
| 8 | **SOC 2 report** | Sold only in enterprise tiers (Webhook Relay Enterprise "SOC2 report" [15]; Trigger.dev Enterprise SOC 2 [6]; Convoy Enterprise "SOC 2 & compliance support" [3]). Irrelevant to the first 50 SMB customers; noise until upmarket motion begins. | **L** — defer |

## 5. Single-VM-Per-Customer Model: Defensibility and Fragility

**The database is not the ceiling.** TimescaleDB benchmarks (vendor-published, cross-checked against an independent competitor benchmark, both cited):
- Timescale's own PostgreSQL comparison: at 1 billion rows on a single machine, "TimescaleDB retained its throughput of 111K rows/s while PostgreSQL's average over the last 100M rows dropped to only 5K rows/s" [16].
- QuestDB's *adversarial* benchmark (a competitor motivated to understate Timescale) still measures ~1.2M rows/s peak, degrading to 480–620K rows/s at very high cardinality [17].
- Timescale 2.21 claims sustained ingestion "exceeding 5 million rows per second" with direct-to-columnstore [18] — treat as vendor ceiling, not planning number.

Workload math: 1M events/month = **0.39 events/second average**. Even 100M events/month = ~39/s. The conservative independent floor (~100K rows/s) is **five orders of magnitude** above the target customer's ingest rate. The actual single-VM ceilings, in the order they will bite:
1. **Disk/retention**: a Hetzner CX32 (4 vCPU/8GB/**80GB**, €6.80/mo [19]) holds roughly 1M events/mo × ~5KB × 12 months ≈ 60GB uncompressed — about a year before compression; Timescale columnar compression (90%+ vendor claim [18]) or a volume upgrade extends this cheaply. Retention policy controls (gap #4) are also a capacity tool.
2. **Outbound delivery fan-out**: 5M HTTP deliveries/month ≈ 2/s average — trivial; the risk is *burst* + slow consumers, which is why circuit breaking/rate limiting (gap #6) protects the ops margin.
3. **Single-node availability**: no HA. A VM failure means downtime until restore. Mitigation is honesty (99.9% SLA, gap #2) + tested off-VM backups with published RPO/RTO (gap #3), not fake five-9s.

**Defensible because**: per-customer isolation makes noisy-neighbor impossible and blast-radius = 1; unit economics are brutal-simple (€6.80–25/mo infra vs £29–79 revenue = 60–90% gross margin per customer at list [19, 20]); the model is legally uncopyable by ELv2/SSPL rivals (§2); and Elestio proves ops automation for one-VM-per-app is a solved, profitable pattern at ~$17/mo [14].
**Fragile because**: ops automation debt compounds per customer; Hetzner repriced upward on 15 June 2026 [20] proving the cost floor moves; and the same OSS-ness that builds trust lets Elestio/PikaPods host EventCore for less (§6).

## 6. Risks of the Model

1. **Self-cannibalization via generic hosts**: EventCore is OSS, so Elestio (~$17/mo, includes backups/updates [14]) or PikaPods (~$2–4/mo [21]) can host it cheaper than EventCore-managed. Counter: they don't know the product, offer no product SLA, no upgrade curation, no DPA specific to event data. The paid tier must sell *expertise + accountability*, not compute. *(Interpretation.)*
2. **Infra cost drift**: Hetzner raised cloud prices effective 15 June 2026 (CPX31 $17.99 → $24.99/mo for new orders [20]). The ~£20/mo premise holds today on CX32 (€6.80 [19]) but is not contractual. Flat pricing absorbs this risk on EventCore's side of the table.
3. **Support economics at £29**: one bad customer consuming hours of support erases a year of margin. Mitigations: docs-first support, community tier for self-hosters, £79 tier for anything interactive. *(Interpretation.)*
4. **No-HA honesty tax**: Hookdeck sells 99.999% [2]. Some buyers will disqualify a 99.9% single-node offer regardless of price. Accept losing them; do not chase HA prematurely.
5. **Low barrier to positioning entry**: EventDock already Show-HN'd "$29/mo (vs $490 alternatives)" in this exact gap [22] — flat-price webhook reliability is not a secret. EventCore's moat is the OSS + self-host + forensic-facet combination, not the price tag alone. (EventDock still meters — $29 caps at 100k events [22] — so "unlimited flat" remains unclaimed.)
6. **Small-vendor trust deficit**: the standard objection to a £29/mo one-person-ops service is "what if you vanish?" The OSS license + data-exit button (gap #3) is the structural answer no proprietary rival can give.
7. **Single-region story**: Hetzner = EU (+ limited US/SG [13]). US-latency-sensitive buyers are out of scope initially; that's a focus choice, not an accident. *(Interpretation.)*

## 7. Why Now

The market has priced itself into a corner: every credible managed option for 1M events/month costs $500–1,200/mo (§1b), the cheapest true product tier below that leaves a documented cliff ("no mid-tier option between free and $490/mo" [10]), and usage-based fatigue is producing public defections (a Hookdeck case study reports a bill falling from ~$600 to $10 on switching [10]) and copycat flat-price entrants (EventDock, 2026 [22]). Meanwhile the structurally cheapest rivals have disqualified themselves from EventCore's exact play: Convoy's ELv2 and Hook0's SSPL legally prevent both true-OSS adoption and third-party managed offerings (§2), Svix's OSS server openly lags its cloud [8], and Convoy's commercial future is publicly doubted even as its code ships [11, 12]. EU-hosted infrastructure with GDPR/ISO-27001 posture is commodity-cheap (€6.80/mo [19]) and single-node TimescaleDB demonstrably sustains five orders of magnitude more ingest than the target customer generates [16, 17] — so the "one flat coffee-budget price, unlimited events, you own the data" offer is, for a narrow window, both economically sound and competitively unanswerable by every incumbent's own license or pricing structure.

## Knowledge Gaps

1. **Svix fan-out billing**: whether a message delivered to 5 endpoints bills as 1 or 5 "attempted messages". Pricing page says "only attempted messages are counted" without defining fan-out multiplication [1]. Affects the 1M-scenario figure by ~$400/mo ($585 vs $985). **Attempted**: svix.com/pricing. **Recommendation**: ask Svix sales or test the API meter before quoting the comparison publicly.
2. **Convoy Cloud pricing**: still unpublished on getconvoy.io/pricing — only self-hosted license tiers are public [3]. **Attempted**: pricing page fetch 2026-07-05 (same result as diverge research). Persistent gap; likely intentional.
3. **EventDock HN reception**: the Algolia API returned the post but no comments — cannot tell whether the thread had zero comments (weak traction signal) or the payload was truncated [22]. Direct HN fetch was rate-limited (HTTP 429). **Recommendation**: re-check thread before citing EventDock traction either way.
4. **Convoy the company vs Convoy the project**: commercial status ("wound down") is asserted only by a competitor [11]; GitHub shows active releases [12]. No independent/primary statement from frain-dev found within budget.
5. **WorkOS Audit Logs default retention window**: carried over unresolved from the diverge research; the steady-state cost model assumes customer-configured 12-month retention.
6. **Hetzner exact current CX32 price post-June-2026 adjustment**: €6.80 figure is from a third-party tracker [19]; Hetzner's own cloud page did not render prices in fetch [13]. Cross-check at order time.

## Full Citations

All accessed 2026-07-05.

[1] Svix. "Pricing". https://www.svix.com/pricing/
[2] Hookdeck. "Event Gateway Pricing". https://hookdeck.com/pricing
[3] Convoy (frain-dev). "Pricing". https://www.getconvoy.io/pricing
[4] WorkOS. "Pricing". https://workos.com/pricing
[5] Inngest. "Pricing". https://www.inngest.com/pricing
[6] Trigger.dev. "Pricing". https://trigger.dev/pricing
[7] AWS. "Amazon EventBridge Pricing". https://aws.amazon.com/eventbridge/pricing/
[8] Svix. "svix-webhooks README". https://github.com/svix/svix-webhooks (feature-lag quote via diverge research, accessed 2026-07-05)
[9] Hook0. "Hook0 vs Svix vs Hookdeck — Webhook Comparison". https://documentation.hook0.com/comparisons — competitor-published, bias noted
[10] Hookdeck. "Svix Dispatch Alternatives" / Outpost comparison pages. https://hookdeck.com/webhooks/platforms/svix-dispatch-alternatives — competitor-published, bias noted
[11] Svix. "Alternatives to Convoy". https://www.svix.com/alternatives/convoy/ — competitor-published, bias noted; contradicted on activity by [12]
[12] frain-dev. "Convoy repository (release v26.6.0, 2026-06-27)". https://github.com/frain-dev/convoy
[13] Hetzner. "Cloud". https://www.hetzner.com/cloud/ (GDPR, ISO 27001, locations, 99.9% SLA)
[14] vikasprogrammer. "I Compared 6 Platforms for Deploying Self-Hosted Apps in 2026". https://dev.to/vikasprogrammer/i-compared-6-platforms-for-deploying-self-hosted-apps-in-2026-3j8 — community source, medium trust, corroborated by elest.io marketing
[15] Webhook Relay. "Pricing". https://webhookrelay.com/pricing/
[16] Timescale. "TimescaleDB vs PostgreSQL" (docs content). https://github.com/timescale/docs.timescale.com-content/blob/master/introduction/timescaledb-vs-postgres.md — vendor benchmark, bias noted
[17] QuestDB. "TimescaleDB vs QuestDB: 2026 Benchmark Results". https://questdb.com/blog/timescaledb-vs-questdb-comparison/ — adversarial competitor benchmark, useful as a floor
[18] Tiger Data (Timescale). "Speed Without Sacrifice: TimescaleDB 2.21". https://www.tigerdata.com/blog/speed-without-sacrifice-37x-faster-high-performance-ingestion-42x-faster-deletes-improved-cagg-updates-timescaledb-2-21 — vendor claim
[19] CostGoat. "Hetzner Cloud VPS Pricing Calculator (Jul 2026)". https://costgoat.com/pricing/hetzner — third-party price tracker
[20] Hetzner. "Price Adjustment 15 June 2026". https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/ ; Northflank. "Hetzner cloud server price increases in 2026". https://northflank.com/blog/hetzner-cloud-server-price-increases
[21] PikaPods. "Instant Open Source App Hosting". https://www.pikapods.com/
[22] Hacker News. "Show HN: EventDock – Webhook reliability for $29/mo (vs $490 alternatives)". https://news.ycombinator.com/item?id=47151522 — comments unretrieved (429), post metadata via hn.algolia.com

## Research Metadata

Tool calls: 12 web operations (7 fetches, 5 searches), 1 fetch failed (HN direct, 429 — recovered via Algolia API, partial). Sources examined: ~30; cited: 22. Cross-referenced: all pricing from official first-party pages; competitor-vs-competitor claims flagged for bias throughout; one conflict documented (Convoy vitality). Confidence distribution: High ~70% (official pricing), Medium ~25% (benchmarks, hosting precedents), Low ~5% (EventDock traction, Convoy company status).
