# EventCore Hosting Feasibility — Is £29 Flat on a Hetzner VM Honest?

**Date**: 2026-07-05 | **Researcher**: nw-researcher (Nova) | **Confidence**: High (Hetzner pricing, retention mechanics), Medium (compression ratios, object-storage overage rates) | **Sources**: 20 (18 external, 2 internal)
**Extends**: `docs/product/market-positioning.md` (commercial lens). This document answers two open questions from that work: (a) is the ~£20/mo infra cost real and stable, and (b) what retention policy makes "unlimited events" at a flat price honest and sustainable.

## Executive Summary

**The infra premise is confirmed and better than assumed — but the floor moves.** Post the 15 June 2026 Hetzner adjustment (verified against the official notice and an independent tracker that agree to the cent), the realistic small-customer VM — CX33: 4 vCPU, 8 GB RAM, 80 GB disk, 20 TB traffic — costs €10.19/mo (~£8.66) including 20% automated backups, not ~£20. However, Hetzner repriced cloud twice in six months in 2026 (April: +30–37%; June: new orders only, CX +27–38%, CPX/CCX far more), driven by DRAM/NVMe inflation. Margins are therefore modeled at both list and +40% stressed prices; the June change protects already-rented servers, so an existing fleet's costs are sticky.

**"Unlimited events" is arithmetically false; retention is the honest unit of price.** From EventCore's actual schema, one 1 KB event fanned out to 3 webhooks stores **~5.9 KB nominal / ~7 KB planned** end-to-end — 75% of it being four copies of the payload (one in `events`, one per outbox delivery row). At 1M events/mo that is 84 GB/yr uncompressed: the £29 VM fills in ~7.5 months. TimescaleDB columnar compression (vendor 90%+; real-world JSON-log production case 88.6%) multiplies capacity ~5–9×, and `drop_chunks` retention makes rotation operationally free — but only if the outbox and attempts tables are converted to hypertables too, since the events hypertable is just 22% of the footprint. Native S3 tiering is Timescale-Cloud-only, so archiving to Hetzner Object Storage (€4.99/mo per TB) is a small DIY job.

**Verdict: £29 flat is viable at ~64% gross margin (50% under a +40% Hetzner shock)** with the promise rewritten as "no per-event metering" plus a fair-use rate and explicit retention: £29 → 100k events/day sustained, 90-day full-fidelity hot retention, 12-month EU archive; £79 → 200k/day, 12-month hot (~76% margin). Overage never bills — it triggers a published rotation ladder (compress sooner → archive sooner → offer upgrade). Competitively this is untouchable on retention: 90 days costs $490/mo at Svix, 30 days costs $499/mo at Hookdeck, and 12-month retention costs ~$1,188/mo at WorkOS.

## Research Methodology

**Search Strategy**: Official Hetzner docs for post-adjustment pricing, cross-checked against a third-party price tracker (exact match on all figures); official TimescaleDB/Tiger Data docs and GitHub sources for compression/retention mechanics; one real-world production case study for compression on JSON-log workloads; bytes-per-event derived from EventCore's shipped Flyway migrations plus PostgreSQL's official storage documentation; competitor retention reused from same-day official-page fetches in `market-positioning.md`.
**Source Selection**: Types: official docs / vendor / industry press / community case study / primary GitHub | Reputation: medium min, official preferred | Verification: all load-bearing prices doubly sourced; vendor performance claims paired with independent or real-world corroboration; bias flagged on all vendor claims.
**Quality Standards**: Every price in §1 verified against 2 sources; compression ratio triangulated (vendor claim + docs + production case); single-source items flagged in Knowledge Gaps with reduced confidence.

## 1. Hetzner Cloud Pricing (post 15 June 2026 adjustment)

All prices exclude VAT, Germany/Finland (FSN/NBG/HEL) regions, verified 2026-07-05 against Hetzner's official price-adjustment notice [H1] and cross-checked against a third-party tracker updated 2026-06-29 [H2] — **the two sources match exactly on every CX/CPX/CCX figure**, so the table is doubly verified. GBP conversions assume €1 ≈ £0.85 (approximate, July 2026; conversion is interpretation, not a quoted price).

### 1a. Compute (new-order prices effective 15 June 2026)

| Plan | vCPU | RAM | Disk | Traffic incl. | €/mo | ≈£/mo | Old €/mo |
|---|---|---|---|---|---|---|---|
| **CX23** (shared Intel) | 2 | 4 GB | 40 GB | 20 TB | **5.49** | 4.67 | 3.99 |
| **CX33** (shared Intel) | 4 | 8 GB | 80 GB | 20 TB | **8.49** | 7.22 | 6.49 |
| **CX43** (shared Intel) | 8 | 16 GB | 160 GB | 20 TB | **15.99** | 13.59 | 11.99 |
| **CX53** (shared Intel) | 16 | 32 GB | 320 GB | 20 TB | **29.49** | 25.07 | 22.49 |
| CPX22 (shared AMD) | 2 | 4 GB | 80 GB | 20 TB | 19.49 | 16.57 | 7.99 |
| CPX32 (shared AMD) | 4 | 8 GB | 160 GB | 20 TB | 35.49 | 30.17 | 13.99 |
| CCX13 (dedicated) | 2 | 8 GB | 80 GB | 20 TB | 42.99 | 36.54 | 15.99 |
| CCX23 (dedicated) | 4 | 16 GB | 160 GB | 20 TB | 85.99 | 73.09 | 31.49 |
| CCX33 (dedicated) | 8 | 32 GB | 240 GB | 30 TB | 138.49 | 117.72 | 62.49 |

Sources: [H1] (official, every old→new price), [H2] (tracker, specs + prices). The CX line was renamed (CX22→CX23 etc.) in Hetzner's 2026 portfolio standardization.

### 1b. Storage, backup, and traffic add-ons

| Item | Price | Source |
|---|---|---|
| Block-storage Volume | **€0.0572/GB/mo** (10 GB–10 TB, 1 GB increments; up to 5,000 IOPS sustained) | [H2], [H3] |
| Automated backups | **+20% of server price** (7 rolling backups) | [H2] |
| Snapshots | €0.0143/GB/mo | [H2] |
| Object Storage (S3-compatible) | **€4.99/mo base incl. 1 TB storage + 1 TB egress**; extra storage €0.0067/TB-hour (≈€5/TB/mo); extra egress €1.00/TB; ingress and S3 ops free | [H4], [H5] |
| Traffic overage (cloud servers) | €1.00/TB (EU/US) beyond the included 20 TB | [H2] |

### 1c. Cost stability — the premise moved, twice

Hetzner adjusted prices **twice for cloud servers in 2026**: 1 April (cloud +30–37% in DE/FI, storage +30%) and 15 June (new orders/rescales only; CX +27–38%, CPX roughly +144%, CCX roughly +169%) [H1], [H6]. The stated driver is DRAM/NVMe cost inflation from AI-infrastructure demand [H6]. Two consequences for EventCore:

1. **The "~£20/mo infra" premise from the positioning doc is actually pessimistic** for small customers: the realistic small-customer VM (CX33: 4 vCPU/8 GB/80 GB) is €10.19/mo ≈ **£8.66 including 20% backups**. Even CX43 with backups is €19.19 ≈ £16.31 — still under £20.
2. **But the floor moves**: two increases in six months, ~30–40% each time on the CX line. Margin models below stress-test at +40% infra. Importantly, the June adjustment "applies to new orders and cloud instance rescales" — **currently rented servers are not affected** [H1], [H6] — so an existing fleet's cost is sticky; only new-customer VMs pay new prices. Flat pricing absorbs drift on EventCore's side, as the positioning doc already noted.

**Which tier runs EventCore?** EventCore is Spring Boot (JVM, ~1–2 GB heap comfortable) + TimescaleDB single node under Docker Compose. CX23 (4 GB RAM) is tight once the JVM, Postgres shared_buffers, and OS share 4 GB — viable for demo, not for a paying customer. **CX33 (4 vCPU/8 GB/80 GB, €8.49) is the realistic small/mid customer tier**; CX43 (16 GB/160 GB) for the upper tier. Dedicated-vCPU CCX is unwarranted: the workload is ~0.4–4 events/s average (positioning doc §5) — five orders of magnitude below single-node TimescaleDB ingest floors. *(Sizing is interpretation grounded in the cited specs.)*

## 2. Bytes-per-Event Derivation (from EventCore's actual schema)

Derived from the shipped migrations (`src/main/resources/db/migration/`): `V2__events.sql` (hypertable: `id UUID, time TIMESTAMPTZ, type TEXT, payload JSONB`, PK `(time,id)`), `V6__events_type_index.sql` (`(type, time DESC, id DESC)`), `V4__webhook_deliveries.sql` (outbox: 3 UUIDs, **`body JSONB NOT NULL` — a full payload copy per delivery**, status/attempts/2 timestamps), `V10__delivery_attempts.sql` (2 UUIDs, attempt INT, timestamp, status_code, error TEXT, response_snippet TEXT, duration_ms, plus `UNIQUE(delivery_id, attempt)`).

Assumptions: 1 KB JSON payload; `type` ~30 chars; fan-out to 3 webhook endpoints; 1.2 delivery attempts per delivery on average (≥90% first-try success); PostgreSQL heap tuple header 23 B + 4 B line pointer per row [P1]; B-tree index tuples ~40–60 B effective each including fill-factor slack; JSONB binary ≈ payload text size +5–10%.

| Component | Per-event calculation | Bytes |
|---|---|---|
| `events` row | 27 overhead + 16 id + 8 time + ~34 type + ~1,100 JSONB + padding | ~1,200 |
| `events` indexes | PK(time,id) ~50 + events_by_type(type,time,id) ~60 | ~110 |
| `webhook_deliveries` × 3 | (27 + 48 UUIDs + ~1,100 body + ~30 status/attempts/timestamps) × 3 | ~3,620 |
| deliveries indexes × 3 | PK ~50 × 3 (partial "due" index ≈ 0 at steady state — pending rows only) | ~150 |
| `delivery_attempts` × 3.6 | (27 + 32 UUIDs + 24 fixed + ~35 avg error/snippet) × 3.6 | ~430 |
| attempts indexes × 3.6 | (PK ~50 + UNIQUE(delivery_id,attempt) ~55) × 3.6 | ~380 |
| **Nominal total** | | **~5.9 KB/event** |

Two structural facts dominate:

1. **The payload is stored 4× end-to-end** (once in `events`, once per delivery in the outbox `body`). ~4.4 KB of the 5.9 KB (~75%) is payload copies. Fan-out multiplies storage, not just egress. *(Design note, labelled interpretation: referencing the event payload from deliveries instead of copying would cut total footprint ~55%, at the cost of losing "body as sent" forensics if events are mutated/redacted — a deliberate trade-off to revisit.)*
2. **MVCC bloat is real for the outbox**: every `webhook_deliveries` row is UPDATEd at least once (`pending → delivered/failed`), creating dead tuples that autovacuum reclaims imperfectly; plus WAL and TimescaleDB chunk metadata. Add ~15–20% headroom.

**Planning number: ~7 KB per event stored, end-to-end, uncompressed** (nominal 5.9 KB × ~1.18 bloat/slack). This refines the positioning doc's ~5 KB estimate upward — the earlier figure omitted the attempt rows and bloat. Scales linearly with payload size: a 4 KB payload ≈ ~19 KB/event.

## 3. Growth Table: Volume × Time × Tier

At ~7 KB/event uncompressed. "Usable" disk = (included disk − 15 GB for OS/Docker/JVM/WAL/temp) × 0.8 Postgres headroom.

**Log growth, uncompressed:**

| Ingest | GB/month | GB/year |
|---|---|---|
| 100k events/mo | 0.7 | 8.4 |
| 1M events/mo | 7 | 84 |
| 10M events/mo | 70 | 840 |

**Time until each tier's included disk fills (uncompressed, no retention policy):**

| Tier (usable) | 100k/mo | 1M/mo | 10M/mo |
|---|---|---|---|
| CX23 (~20 GB) | ~2.4 yr | ~3 months | ~9 days |
| CX33 (~52 GB) | ~6 yr | ~7.5 months | ~3 weeks |
| CX43 (~116 GB) | ~14 yr | ~17 months | ~7 weeks |
| CX53 (~244 GB) | ~29 yr | ~35 months | ~3.5 months |

**Reading**: "unlimited events forever" is arithmetically false at any tier once a customer does ≥1M/mo. Without compression + retention, a £29 CX33 instance at 1M/mo fills its disk inside 8 months. Block-storage volumes extend capacity at €0.0572/GB/mo (+100 GB = €5.72/mo [H2]) but only postpone the question. The flat price must therefore be anchored to a **retention window**, not an event count.

## 4. TimescaleDB Compression and Retention Mechanics

**Compression ratios (evidence).** Vendor claim: columnar compression achieves "90%+" reduction (TimescaleDB 2.21 announcement) [T2]; the docs describe ratios of 10–20× for typical time-series [T6]. Real-world, closest analog to EventCore's workload: **Logtide (open-source log platform, JSONB metadata + text messages, 500k logs/day production data) measured 220 GB → 25 GB = 88.6% (~8.8×)** using `compress_after INTERVAL '7 days'`, `segmentby` on low-cardinality columns, `orderby timestamp DESC`; aggregations got 41% faster, full-text search 12% slower [T3]. JSONB compresses via dictionary + LZ-class compression rather than the numeric codecs, so payload-heavy tables land below the 20× headline [T6]. **Planning ratios: 5× conservative, ~9× observed-likely.**

**Impact on the growth table** (compress after 7 days, retention window R, steady state):

| Scenario | Effective KB/event (blended) | CX33 sustains (90-day retention) | CX43 sustains (365-day retention) |
|---|---|---|---|
| Uncompressed | 7.0 | ~82k/day (2.5M/mo) | ~45k/day (1.4M/mo) |
| 5× (conservative) | 1.4 after day 7 | ~315k/day (9.4M/mo) | ~210k/day (6.3M/mo) |
| 9× (observed) | 0.78 after day 7 | ~457k/day (13.7M/mo) | ~353k/day (10.6M/mo) |

Compression turns the CX33 from "fills in 7.5 months at 1M/mo" into "sustains ~9M/mo indefinitely at 90-day retention" — a 4–6× capacity multiplier on the honest promise.

**Schema caveat — the single most important engineering finding**: today only `events` is a hypertable (`V2__events.sql`); `webhook_deliveries` and `delivery_attempts` are plain PostgreSQL tables. The events table is only ~22% of the per-event footprint (§2) — **compressing it alone saves just ~20%**. To reach the ratios above, the outbox and attempts tables must also become hypertables (both are naturally time-keyed on `created_at`/`attempted_at`) with their own compression + retention policies. Note the FK chain (`delivery_attempts.delivery_id → webhook_deliveries.id`, and the hypertable PK-must-include-time rule) needs migration work — see Knowledge Gaps #3.

**Retention mechanics.** `add_retention_policy()`/`drop_chunks()` drop whole chunks — "dropping data by the chunk is faster" than row `DELETE`s because it "deletes an entire file from disk" with no garbage collection or defragmentation [T1]. Rotation is effectively free at any scale, which is what makes a scheduled retention promise operationally cheap on a small VM.

**Archive tier.** Timescale's native tiered storage to S3 is **Timescale Cloud only** — self-hosted support is an open feature request (timescale/timescaledb#6632, open since Feb 2024) [T4], [T5]. EventCore must DIY the archive: a scheduled job that exports chunks older than the hot window (`COPY ... TO` CSV/Parquet, or per-chunk dump) to **Hetzner Object Storage (S3-compatible, €4.99/mo incl. 1 TB storage + 1 TB egress; ingress and internal EU-zone traffic free [H4], [H5])** before `drop_chunks` runs. 1 TB of archive ≈ 143M uncompressed events (≈1.4B at 10× compressed export) — one €4.99 bucket covers the entire early customer fleet. Effort: S–M (cron + rclone/aws-cli, restore runbook).

## 5. Competitor Retention at Each Price Point

From official pricing pages, verified 2026-07-05 (full working in `market-positioning.md` §1):

| Vendor / tier | Price | Retention |
|---|---|---|
| Hookdeck Free / Team / Growth | $0 / from $39 / from $499 | **3 days / 7 days / 30 days** [C2] |
| Svix Free / Pro | $0 / $490+ | **30 days / 90 days** [C1] |
| Convoy self-hosted Premium | $999/mo license | "Advanced webhook retention" is a paid gate [C3] |
| WorkOS Audit Logs | $99/mo per 1M events **retained** | Retention *is* the meter — 12-month retention at 1M/mo ≈ $1,188/mo [C4] |
| Inngest (traces) | $0 / from $99 | 24h / 7d / 90d traces [C5] |

**Reading**: the market prices retention, not ingest. 90-day retention costs $490/mo (Svix Pro); 30-day costs $499/mo (Hookdeck Growth); 12-month costs ~$1,188/mo (WorkOS). Nobody at any listed price offers 12-month full-fidelity delivery forensics. So a £29 tier with a **90-day** promise matches the most expensive mainstream retention window at 1/16th the price, and a £79 tier with **12 months** is an offer that literally does not exist below ~$1,200/mo. Retention is where EventCore's flat price is most differentiated — and it is exactly the axis the disk-fill math forces us to define anyway.

## 6. Recommended Price/Retention Matrix

Principle: **the flat price buys a retention window and a fair-use sustained rate, never a per-event meter.** Fair-use rates are set at ≤⅓ of conservative (5×-compression) capacity, so the promise survives worst-case compression, payload bloat, and Hetzner repricing. Infra costs use post-June-2026 new-order prices + 20% backup + ~£1–1.5 archive/monitoring share; GBP at €1≈£0.85.

| Tier | Price | VM | Fair-use sustained | Hot retention (full fidelity: payload + attempts) | Archive | Infra cost | Gross margin | Margin at +40% Hetzner |
|---|---|---|---|---|---|---|---|---|
| **Starter** | **£29/mo** | CX33 (4 vCPU/8 GB/80 GB) | **100k events/day** (~3M/mo), 3-endpoint fan-out | **90 days** (compress after 7) | 12 months in EU object storage, restore on request | ~**£10.5** (€10.19 VM+backup ≈ £8.66 + ~£1.8 archive/monitoring) | **~64%** | ~50% |
| **Retain** (optional) | **£49/mo** | CX33 (same VM) | 75k events/day (~2.3M/mo) | **12 months** | 24 months | ~£10.5 | **~79%** | ~69% |
| **Team** | **£79/mo** | CX43 (8 vCPU/16 GB/160 GB) | **200k events/day** (~6M/mo) | **12 months** | 24 months | ~**£19** (€19.19 ≈ £16.31 + ~£2.7) | **~76%** | ~66% |
| Custom | from £149/mo | CX53 + volumes | 400k+/day (~12M+/mo) | negotiated | negotiated | ~£33+ | ~75% | ~68% |

Capacity honesty check (conservative 5× compression): Starter fair-use 100k/day vs ~315k/day capacity (3.1× headroom); Retain 75k/day vs ~94k/day (1.25× — the tightest; relief valve is a £4.86/100 GB volume); Team 200k/day vs ~210k/day at 5× and ~353k/day at 9× (volume add-on absorbs the 5× worst case for pennies).

**Overage = rotation, never a bill.** The disk-pressure ladder, published as policy:
1. 70% disk: alert (ours and customer's).
2. 80%: compress-after shortens automatically (7 days → 2 days).
3. 85%: oldest chunks archive to object storage *earlier than the promised window* only after notifying the customer, alongside an offer: upgrade tier or add a volume at transparent cost (+£5 per 100 GB/mo, at-cost).
4. Ingest is never blocked and no invoice ever changes without the customer initiating it.

*(Interpretation, flagged)*: this ladder converts the only genuine "unlimited" lie — infinite disk — into a visible, contractual rotation behavior, which is precisely what "flat price, no surprises" needs to be true.

**Marketing language this math supports**: not "unlimited events" but **"no per-event metering — we never count your events against a bill"**, with the fair-use rate and retention window stated per tier. Every number above is defensible on cited infrastructure prices.

## 7. Traffic and Egress Check

Every Hetzner cloud tier under consideration includes **20 TB/mo of outbound traffic**; overage is €1.00/TB in EU/US [H2]. A webhook delivery ≈ 1 KB payload + headers + signature + TLS framing ≈ ~2 KB on the wire, ×1.2 for retries:

| Scenario | Deliveries/mo | Egress/mo | % of included 20 TB |
|---|---|---|---|
| 1M events × 3 endpoints | 3.6M (incl. retries) | ~7.2 GB | 0.04% |
| 10M events × 3 endpoints | 36M | ~72 GB | 0.36% |
| Break-even (20 TB) | ~10 **billion** 2 KB deliveries | 20 TB | 100% |

Off-VM backups to Hetzner Object Storage ride the free internal EU-zone traffic; ingress is free [H4]. Even if the entire 20 TB were somehow exhausted, overage at €1/TB means a pathological 40 TB month costs €20. **Egress is never a cost concern for this business** — the concern with fan-out is storage (§2: the payload copied per delivery) and burst/slow-consumer handling, not bandwidth.

## Verdict

**£29 flat is viable — at ~64% gross margin on today's post-increase Hetzner prices, holding ~50% even under a further +40% infra shock — provided "unlimited events" is retired in favor of "no per-event metering" with a stated fair-use rate (100k events/day) and a retention promise (90 days full-fidelity hot, 12-month EU archive).** The infra premise is better than the positioning doc assumed (£8.66/mo VM+backup vs the ~£20 premise), but it is not stable — Hetzner repriced cloud twice in 2026 [H1], [H6] — so margins must be modeled at stressed prices, as above. The disk, not the CPU, is the real meter: uncompressed, a 1M events/mo customer fills the £29 VM in ~7.5 months, which is why the retention window — enforced cheaply by TimescaleDB `drop_chunks` [T1] and multiplied ~5–9× by columnar compression [T2], [T3] — must be the contractual unit of the flat price. Two engineering prerequisites make the math true: extend hypertable + compression treatment to `webhook_deliveries` and `delivery_attempts` (78% of the per-event footprint sits outside the events table today), and build the DIY chunk-archive-to-Object-Storage job, since Timescale's native S3 tiering is cloud-only [T4]. With those in place, the £29/90-day and £79/12-month tiers offer retention windows that cost $490–$1,188/month everywhere else in the market [C1], [C2], [C4].

## Knowledge Gaps

1. **Hetzner Object Storage overage rate precision**: the official page renders prices via JS (placeholders in fetch); €4.99 base / €0.0067 per TB-hour / €1 per TB egress are corroborated by Hetzner's press release and two third-party sources via search but not read off the rendered official pricing table. **Attempted**: hetzner.com/storage/object-storage, docs.hetzner.com overview. **Recommendation**: confirm in the Hetzner console before publishing customer-facing archive pricing. Confidence: Medium-High.
2. **EventCore-specific compression ratio unmeasured**: 5×/9× planning ratios rest on a vendor claim [T2] and one real-world JSON-log case [T3]; EventCore's exact payload mix is untested. **Recommendation**: benchmark with 10M synthetic events (1 KB JSONB, 3-endpoint fan-out) on a CX33 before printing fair-use numbers on the pricing page; adjust `segmentby` (candidate: `type` for events, `subscription_id,status` for deliveries).
3. **Hypertable conversion of the outbox has FK/PK constraints**: TimescaleDB requires the partitioning column in PKs (see `V2__events.sql` comment) and has historical limitations on foreign keys referencing hypertables — `delivery_attempts.delivery_id → webhook_deliveries(id)` may need to be dropped, app-enforced, or redesigned when `webhook_deliveries` becomes a hypertable. **Attempted**: not researched in budget. **Recommendation**: verify current FK-to-hypertable support in TimescaleDB ≥2.16 docs before the migration.
4. **CX33 RAM adequacy under compression jobs**: JVM + Postgres + compression/archive jobs in 8 GB is asserted from sizing experience (interpretation), not load-tested. **Recommendation**: soak test at fair-use rate ×3 on CX33.
5. **GBP/EUR drift**: Hetzner bills in EUR; £-flat pricing carries unhedged FX risk on top of repricing risk (~±5% margin swing per 5p move). Not researched further; note for financial planning.

## Conflicting Information

None material. The positioning doc's "CX32 €6.80" [its ref 19] vs this doc's CX33 €8.49 is not a conflict: Hetzner renamed and repriced the line on 15 June 2026 [H1]; the tracker figures the positioning doc used predate the change taking full effect on new orders. This doc supersedes those numbers.

## Full Citations

Accessed 2026-07-05 unless noted.

**Hetzner**
[H1] Hetzner. "Price Adjustment 15 June 2026". https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/ — official; every CX/CPX/CCX old→new price.
[H2] CostGoat. "Hetzner Cloud VPS Pricing Calculator". https://costgoat.com/pricing/hetzner — third-party tracker, data dated 2026-06-29; matches [H1] exactly on all overlapping figures.
[H3] Hetzner. "Cloud Volumes Overview". https://docs.hetzner.com/cloud/volumes/overview/ — official; limits and performance (price via [H2]).
[H4] Hetzner. "Object Storage Overview". https://docs.hetzner.com/storage/object-storage/overview/ — official; quotas, free ingress/internal traffic, S3 compatibility.
[H5] Hetzner. "Object Storage" product/press pages. https://www.hetzner.com/storage/object-storage/ and https://www.hetzner.com/press-release/object-storage/ — €4.99 base incl. 1 TB storage + 1 TB egress; €0.0067/TB-hr, €1/TB (page JS-renders prices; figures corroborated via search results incl. european-alternatives.eu — see Gap #1).
[H6] webhosting.today. "Hetzner has now raised prices three times in 2026 — this one is different". 2026-05-29. https://webhosting.today/2026/05/29/hetzner-has-now-raised-prices-three-times-in-2026-this-one-is-different/ — industry press; 2026 repricing timeline and DRAM/NVMe cause.

**PostgreSQL / TimescaleDB**
[P1] PostgreSQL Documentation. "Database Page Layout" (23-byte heap tuple header, line pointers). https://www.postgresql.org/docs/current/storage-page-layout.html — official, evergreen.
[T1] Timescale. "About data retention" (docs source). https://github.com/timescale/docs/blob/latest/use-timescale/data-retention/about-data-retention.md — official; chunk drops delete whole files, no GC/defrag.
[T2] Tiger Data (Timescale). "TimescaleDB 2.21" announcement (90%+ compression claim). https://www.tigerdata.com/blog/speed-without-sacrifice-37x-faster-high-performance-ingestion-42x-faster-deletes-improved-cagg-updates-timescaledb-2-21 — vendor claim, bias noted.
[T3] Polliog. "TimescaleDB Compression: From 150GB to 15GB (90% Reduction, Real Production Data)". dev.to. https://dev.to/polliog/timescaledb-compression-from-150gb-to-15gb-90-reduction-real-production-data-bnj — community source, medium trust; concrete production measurements (Logtide), corroborates [T2].
[T4] timescale/timescaledb. "[Feature]: Tiered Storage for Self Hosting" (open issue #6632, Feb 2024). https://github.com/timescale/timescaledb/issues/6632 — primary evidence S3 tiering is not self-hostable.
[T5] Tiger Data docs. "Tiered Storage" / "About Tiger Cloud storage tiers". https://docs.tigerdata.com/use-timescale/latest/data-tiering/ — official; tiering is a cloud-service feature.
[T6] Tiger Data. "Postgres TOAST vs. Timescale Compression" (JSONB dictionary+LZ handling; 10–20× typical). https://www.tigerdata.com/blog/postgres-toast-vs-timescale-compression — vendor, surfaced via search; verification level: search summary, not full fetch.

**Competitors** (all official pricing pages, fetched 2026-07-05 for `market-positioning.md`; retention figures reused, not re-fetched)
[C1] Svix. "Pricing". https://www.svix.com/pricing/
[C2] Hookdeck. "Pricing". https://hookdeck.com/pricing
[C3] Convoy. "Pricing". https://www.getconvoy.io/pricing
[C4] WorkOS. "Pricing". https://workos.com/pricing
[C5] Inngest. "Pricing". https://www.inngest.com/pricing

**Internal**
[E1] EventCore migrations: `src/main/resources/db/migration/V2__events.sql`, `V4__webhook_deliveries.sql`, `V6__events_type_index.sql`, `V10__delivery_attempts.sql` — schema ground truth for §2.
[E2] `docs/product/market-positioning.md` (2026-07-05) — competitor cost scenarios, TimescaleDB throughput benchmarks, business-model risks.

## Research Metadata

Web operations: 12 (9 fetches, 3 searches); 2 fetch dead-ends recovered (Hetzner JS-rendered pages → docs/tracker; tigerdata docs redirect/404 → GitHub docs source). Sources examined ~25, cited 20. Cross-verification: Hetzner compute prices matched exactly across official notice and independent tracker (High); compression ratios triangulated vendor + production case (Medium); object-storage overage search-corroborated only (Medium-High, Gap #1). Confidence distribution: High ~65%, Medium ~30%, Low ~5%. Output: `docs/product/hosting-feasibility.md`. No other files modified.
