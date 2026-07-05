# EventCore Dashboard & Billing Research — What Competitor UIs Show, and How the Commercial Loop Automates

**Date**: 2026-07-05 | **Researcher**: nw-researcher (Nova) | **Confidence**: High (dashboard capabilities, Paddle/Stripe fees, VAT rules), Medium (Lemon Squeezy/Managed Payments status) | **Sources**: 31
**Extends**: `docs/product/market-positioning.md` (pricing lens) and `docs/product/hosting-feasibility.md` (infra/retention lens). This document answers two new questions: (A) what do Svix/Hookdeck/Convoy/WorkOS dashboards actually show, and what is table-stakes for EventCore's planned Next.js tenant-scoped dashboard; (B) how a solo UK founder fully automates payment → provision → credentials → lifecycle for a self-hostable SaaS at £29–79/mo.

## Executive Summary

**Dashboards (Part A).** Across Svix, Hookdeck, Convoy, and WorkOS, eight capabilities recur as table stakes: an event list with payload inspector, per-attempt delivery timelines (status code + response body), UI retry (single + bulk recovery), status/date/endpoint filtering, endpoint management with visible health, delivery metrics charts, a consumer-facing scoped portal, and an export path. EventCore's existing API (events, deliveries + attempt histories, redelivery, `/metrics`) already supplies the data for all eight — the dashboard work is bindings, not backend. The division of labor in the market is sharp: Hookdeck has the best operator metrics (free, p95/p99 latency, queue depth, Prometheus/Datadog export) but no consumer portal and no self-host; Svix has the best consumer portal (iframe/React embed, full white-labeling) but publicly undocumented operator analytics; Convoy has both but gates white-labeling and circuit-breaking behind its $999/mo license; WorkOS is audit-viewing only, with no delivery concept. Nobody shows pull-consumer fleet/lag, and nobody surfaces retention/disk budget to the tenant — EventCore's two cheapest genuine differentiators, since both screens fall directly out of its pull-subscription and rotation-ladder designs.

**Commercial loop (Part B).** The decision is tax, not fees: a UK founder selling B2C digital services into the EU owes destination-country VAT from the first sale with no threshold, remediable only by quarterly non-Union OSS filings — or by a merchant of record who takes the liability. Paddle (5% + 50¢, GA, self-serve, dunning included) costs ~£1.85 on a £29 subscription versus ~£0.99–1.28 for Stripe direct; the ~£0.85/customer/month difference is the cheapest possible price for never touching EU VAT. Lemon Squeezy is no longer a safe choice: it is being absorbed into invite-gated Stripe Managed Payments (same 5% + $0.50) — worth revisiting at public GA. The full loop automates with one small control-plane service: Paddle webhook (`subscription.created`) → Hetzner `POST /v1/servers` with cloud-init user_data (docker compose up EventCore + TimescaleDB + Caddy) → health-check → magic-link email to a portal holding credentials. Lifecycle: no service impact during Paddle's 30-day dunning (an event log with ingest gaps is unrecoverable), then pause-not-cancel → snapshot + delete the VM (powered-off Hetzner servers bill at full rate) → 30-day export grace → deletion with notice. Estimated all-in commercial overhead at £29: ~£1.85 Paddle + ~£0.40 snapshot/backup carrying costs — the ~64% gross margin from `hosting-feasibility.md` survives intact at ~58–60%.

## Research Methodology

**Search Strategy**: Official product documentation (docs.svix.com, hookdeck.com/docs, getconvoy.io/docs, workos.com/docs) for dashboard capabilities; official pricing/docs for Paddle, Lemon Squeezy, Stripe; official docs + community reports for provisioning automation patterns (Elestio, PikaPods, Cloudron, Hetzner hcloud API).
**Source Selection**: Types: official docs (preferred) / vendor marketing (bias-flagged) / community | Verification: capability claims read from vendor's own docs; fee figures doubly sourced where possible.
**Quality Standards**: Per research-methodology adaptive tiers; vendor self-descriptions of their own UI treated as authoritative for "what the UI shows" (single-source acceptable), bias-flagged for quality judgements.

## Part A — Competitor Dashboard/UI Capabilities

### A1. Svix (App Portal + Dashboard)

**Consumer-facing App Portal** (embedded in the Svix customer's own product): "Your customers don't need a Svix account, and they don't even need to know that Svix exists" [S1]. Capabilities documented:
- Endpoint management: add/create/edit/manage webhook endpoints; pick event types per endpoint [S1], [S3].
- "Inspect and replay past webhooks" — message list, payload view, per-attempt details, retry/replay of past events [S1].
- Access: session-based magic links (no password); embedding via **iframe or the `svix-react` component** [S1].
- White-labeling: custom color palettes (light/dark), fonts, logos; query-param control of primary colors, icons, font family, dark-mode default, navigation visibility, padding; read-only mode via `ViewBase`; feature flags to restrict event-type visibility [S1].

**Operator dashboard** (Svix's customer): three documented recovery surfaces — "manually retry each message at any time, automatically retry ('Recover Failed') all failed messages starting from a given date, or replay messages that have never been attempted to an endpoint ('Replay Missing')" [S2]. Endpoint health is automated: "If all attempts to a specific endpoint fail for a period of 5 days, the endpoint will be disabled", with an operational webhook `message.attempt.exhausted` [S2]. Retry schedule surfaced: immediate, 5s, 5m, 30m, 2h, 5h, 10h, 10h [S2].

**Confidence**: High (official docs). Note: Svix's public docs describe the *portal* richly but the operator dashboard thinly — its metrics/analytics screens are not publicly documented (see Knowledge Gaps).

### A2. Hookdeck (Dashboard)

**Operator-only** — no consumer-facing embeddable portal exists (Hookdeck is proprietary receive-side infrastructure; consistent with `market-positioning.md` §2). Documented dashboard surfaces [H1]:
- **Requests** (inbound HTTP received from a source), **Events & Attempts** (outbound deliveries), **Bookmarks** ("catalog and replay specific requests"), **Issues & Notifications** ("track problems and communicate resolutions with your team") with configurable Issue Triggers, and **Metrics**.
- Plus a public **Console** playground: "a playground to receive, inspect, replay and test events" (console.hookdeck.com) [H1].

**Events screen detail** [H2]:
- Event list, descending chronological, 100/page; five statuses (Pending, Successful, Failed, On Hold, Cancelled).
- Attempt inspection: "Select any of the other attempts to view their specific response data" — per-attempt status, date, attempt number, destination response body + HTTP status code. Sidebar shows headers, path, body, query, connection, and full attempt history.
- Retries: "50 automatic retries" plus "manually retried events as many times as you like" (`X-Hookdeck-Attempt-Trigger: MANUAL`).
- Filtering: by status, date/interval, **request content** ("a partial JSON match of the request headers, body, or query; or a partial string match of the request path"), HTTP response code/interval/error code, and by connection/source/destination. Limitation: "Filtering the request property is not supported for payloads exceeding 2.5 MB."

**Metrics** (best-in-class of the four; "available for free to all users") [H3]:
- Source metrics: requests, request rate, accepted/rejected/discarded counts.
- Connection metrics: events created, success/failure counts, "events failure ratio", avg attempts/event, scheduled-retry/paused/delayed events.
- Destination metrics: pending events, "oldest pending attempt", delivered/successful/failed attempts, "avg. response latency" plus p95/p99/max, attempts failure rate.
- UX: time-range picker, per-resource metric pages, "Explore" drill-down from chart to underlying filtered data; query via dashboard, CLI (`metrics events|requests|attempts|queue-depth|pending`), or API.
- **Metrics export** to Datadog, Prometheus/Grafana, New Relic with pre-built dashboard templates — free tier.
- Recommended health indicators: delivery success rate >99%, retry rate alert >5%, p95 end-to-end latency alert >5s, queue-depth growth, open Issues.

**Confidence**: High (official docs, three pages).

### A3. Convoy (Web UI + Customer-Facing Portals)

**Operator dashboard** [C1]:
- **Events Log**: "all webhook events pushed to Convoy"; events "do not have a status and do not contain delivery attempts" (clean event/delivery separation — same model as EventCore's events vs deliveries).
- **Event Deliveries**: six states (Scheduled, Processing, Success, Retry, Failed, Discarded — Discarded = "endpoint has been set to inactive").
- Attempt detail: request+response headers and payload, HTTP status codes, error info, source IP, timestamps, end-to-end latency, human-readable outcome, and a **timeline view for retried deliveries** (newest first).
- Retry: **Retry** button on Failed; **Force Retry** on Success ("retry a successful event in case of a false positive"); batch retry exists via API [C3].
- Filtering: "filter events and event deliveries by date, time, status and endpoints respectively"; **full-text payload search**: "search the entire payload for any value in any field" (search tokens retained until retention expiry).

**Consumer-facing Portal Links** [C2]: "a customer-facing dashboard to display information on an endpoint's event deliveries", scoped to one or more endpoints; deliveries can be "filtered, retried and expanded to view more details". Generated as one-time links from the dashboard or long-use links via API "to be embedded in your dashboards". Links "do not expire; to disable you have to explicitly call the API or disable it through the dashboard". Theming/white-labeling of the portal is not documented — and white-labelling is gated behind the $999/mo Premium license (per `market-positioning.md` §2 [E2]).

**Confidence**: High (official docs) for capabilities; Medium for portal theming absence (absence of documentation, not documented absence).

### A4. WorkOS (Audit Log Viewer + Admin Portal)

**Operator-facing (WorkOS Dashboard)** [W1], [W3]:
- Event schema configuration: "Define the actions, targets, and metadata that you want to record from your app" with a JSON schema editor.
- Event viewing scoped by Organization; CSV export ("Audit Log Export... exported from WorkOS as a CSV file").

**Consumer-facing (Admin Portal — hosted by WorkOS, not embedded)** [W2], [W3]:
- "An out-of-the-box UI for IT contacts to verify domains, configure SSO and Directory Sync connections, and more", "fully maintained and hosted by WorkOS". Intents include `audit_logs` and `log_streams`.
- Audit log viewer: "Filter logs by Actors, Events, Targets, and Dates"; CSV export ("CSV export is ready for download").
- Self-serve SIEM streaming: "Stream Audit Logs to your customer's existing SIEM provider" (Splunk, Datadog) — "They can set it up themselves... directly from the WorkOS Admin Portal."
- Linking: dashboard-generated links (30-day expiry) or API/SDK-generated links (5-minute expiry) placed behind the customer's own auth. **No branding/theming options documented.**

**Confidence**: High for capabilities (official docs + product page); WorkOS has no delivery/attempt concept — it is an audit viewer, not a delivery tool.

### A5. Capability Matrix (cited)

Legend: **O** = operator-facing, **C** = consumer-facing (embedded/linked into the vendor's customer's product), — = absent/undocumented.

| Capability | Svix | Hookdeck | Convoy | WorkOS |
|---|---|---|---|---|
| Event/message list + payload inspector | O + C [S1] | O [H2] | O + C [C1][C2] | O + C [W1][W3] |
| Per-attempt delivery timeline (status, response body) | O + C [S1][S2] | O [H2] | O + C (timeline view) [C1] | — (no delivery concept) |
| Manual single retry from UI | O + C [S1][S2] | O [H2] | O + C [C1][C2] | — |
| Bulk recovery ("Recover Failed" / batch retry) | O ("Recover Failed", "Replay Missing") [S2] | O (auto ×50 + unlimited manual) [H2] | O (batch retry API; Force Retry in UI) [C1][C3] | — |
| Filtering by status/date/endpoint | C [S1] | O (richest: incl. JSON content match) [H2] | O + C [C1][C2] | C (actors/events/targets/dates) [W3] |
| Payload/content search | — | Partial-JSON filter (≤2.5 MB) [H2] | **Full-text payload search** [C1] | — |
| Endpoint management in UI | C (add/edit endpoints) [S1] | O (connections/destinations) [H1] | O | n/a |
| Endpoint health surfaced (auto-disable/circuit state) | Auto-disable after 5 days + ops webhook [S2] | Issues + failure-rate metrics [H1][H3] | Discarded state; circuit breaking is $999-gated [C1][E2] | n/a |
| Metrics charts | — (undocumented) | **Free, p95/p99 latency, queue depth, drill-down** [H3] | Metrics page exists [C3] | — |
| Metrics export (Prometheus/Datadog) | — | Datadog, Prometheus/Grafana, New Relic + templates [H3] | OpenTelemetry export is Premium-gated [E2] | SIEM streaming (Splunk/Datadog), $125/mo/stream [W3][E2] |
| Consumer-facing portal | **iframe + React component** [S1] | **None** | Portal Links (link or embed) [C2] | Admin Portal (hosted, linked) [W2] |
| Portal theming/white-label | Colors, fonts, logos, dark mode [S1] | n/a | Undocumented; white-label $999-gated [E2] | None documented [W2] |
| CSV/log export | — | — | — | CSV export (O + C) [W1][W3] |
| Alerting/issue tracking in UI | Ops webhooks only [S2] | **Issues + Issue Triggers + notifications** [H1] | — | — |

### A6. Table-Stakes Capabilities and Opportunity Gaps

**Table stakes** — capabilities present in at least 3 of 4 (or in every delivery-focused tool), which EventCore's Next.js dashboard must ship to be credible:

1. **Event list + payload inspector** — tenant-scoped list with JSON payload view (all four vendors) [S1][H2][C1][W1].
2. **Per-delivery attempt timeline** — each attempt with status code, response body/snippet, timestamp, latency (Svix, Hookdeck, Convoy) [S2][H2][C1]. EventCore's `delivery_attempts` table already stores exactly this (status_code, error, response_snippet, duration_ms).
3. **Retry from the UI** — single retry on a failed delivery, plus bulk recovery from a point in time (Svix "Recover Failed"/"Replay Missing"; Convoy Retry/Force Retry/batch; Hookdeck manual retries) [S2][C1][H2]. EventCore's single+bulk redelivery API already exists; this is a UI binding.
4. **Filtering by status/date/endpoint/type** — universal [H2][C1][S1][W3]; Hookdeck sets the bar with content-based JSON matching.
5. **Endpoint/subscription management with visible health** — endpoint CRUD plus surfaced state: disabled/circuit-open/failure streak (Svix auto-disable, Convoy Discarded, Hookdeck Issues) [S2][C1][H1].
6. **Delivery metrics overview** — success rate, failure ratio, attempts, latency percentiles, pending/queue depth (Hookdeck free tier is the benchmark) [H3]. EventCore's `/metrics` endpoint feeds this.
7. **Consumer-facing scoped portal** — 3 of 4 vendors monetize an embeddable/linkable portal scoped to one consumer's endpoints (Svix App Portal, Convoy Portal Links, WorkOS Admin Portal) [S1][C2][W2]. For EventCore this maps naturally onto tenant-scoping.
8. **Export path** — CSV export of the log view (WorkOS) and/or metrics export to Prometheus/Datadog (Hookdeck) [W1][H3]. EventCore's data-exit positioning (`market-positioning.md` gap #3) makes this a brand promise, not just a feature.

**Opportunity gaps — what nobody does well** *(interpretation, grounded in the matrix)*:

- **Pull-consumer fleet + lag view**: no vendor shows a screen for pull-based subscribers — their fleet, cursor positions, and lag. Hookdeck's "oldest pending attempt" [H3] is the nearest analog. EventCore's pull-subscription lag data is a genuinely unclaimed, Kafka-console-style differentiator in the webhook space.
- **Retention/disk budget visibility**: nobody surfaces the retention window or storage consumption to the tenant. EventCore's rotation-ladder policy (`hosting-feasibility.md` §6) *requires* exactly this screen ("you are at 70% of your 90-day window") — turning an internal constraint into a trust feature no competitor has.
- **One forensic thread**: event → all deliveries → all attempts in a single correlated view. Delivery tools do attempts well but have no audit/actor lens; WorkOS does audit viewing well but has no delivery concept. EventCore's schema (events + deliveries + attempts, one DB) can render the full thread on one screen.
- **Full-text payload search** is rare (Convoy only [C1]; Hookdeck caps content filters at 2.5 MB [H2]). Postgres/Timescale full-text or JSONB indexing makes this cheap for EventCore.
- **Ungated basics**: circuit-breaking visibility and white-labeling are $999-gated at Convoy [E2]; portal theming is absent at WorkOS. Shipping these flat-price/free extends the "they gate it, we don't" pattern from `market-positioning.md`.
- **Operator metrics parity at the low end**: Svix's operator analytics are publicly undocumented; Hookdeck's are excellent but Hookdeck has no self-host and no consumer portal. A tenant-scoped dashboard that has *both* Hookdeck-grade metrics *and* a Svix-grade portal does not exist in one product today.

## Part B — The Commercial Loop for a Tiny Self-Hostable SaaS

### B1. Merchant of Record vs Stripe Direct

**The tax question decides this, not the fees.** A UK business selling B2C digital services to EU consumers has **no VAT threshold** — "UK providers have no de minimis for B2C digital sales into the EU... must charge destination country VAT from first sale" [B10], [B11]. The remedy without an MoR is registering for the EU **non-Union OSS** scheme in one member state and filing quarterly returns covering all EU B2C sales at each customer's local rate [B10], [B11], [B12]. B2B sales to EU businesses with valid VAT IDs reverse-charge instead — "the scheme only covers business-to-consumer (B2C) transactions" [B11] — but a self-serve checkout at £29 *will* attract sole traders and individual developers without VAT numbers, so the OSS obligation triggers on the first such sale. A merchant of record (Paddle; Lemon Squeezy/Stripe Managed Payments) removes this entirely: the MoR is the legal seller and handles "full tax registration, filing and remittance" with "cross-border sales tax compliance" [B1].

**Vendor status (July 2026):**
- **Paddle** — GA merchant of record, "5% + 50¢ per Checkout transaction", all-inclusive: tax registration/filing/remittance, "chargebacks and fraud protection", "advanced churn recovery", localized checkout, 24/7 support. Caveat: "products under $10... contact Paddle" — EventCore's £29 floor clears this [B1].
- **Lemon Squeezy** — acquired by Stripe (July 2024); being folded into **Stripe Managed Payments**, Stripe's MoR product at the identical "5% + $0.50", currently supporting "merchants in 35+ countries and expanding to more later in 2026", with public access "very soon" (still invite-gated at research time) [B3], [B4]. Direct fetches of lemonsqueezy.com and the Stripe Managed Payments pages were blocked/404 (3 attempts) — status is search-corroborated only, **Medium confidence** (see Knowledge Gaps).
- **Stripe direct (UK account)** — not an MoR. Fees: standard UK cards "1.5% + 20p", EEA cards "2.5% + 20p", international "3.25% + 20p", "+2%" currency conversion; Stripe Billing pay-as-you-go "0.7% of Billing volume"; Stripe Tax Basic "0.5% per transaction, where you're registered" (calculation, not remittance — filing remains the merchant's problem or a separate paid product, Tax Complete "starting at £70.00 per month") [B2], [B12].

**Webhook APIs for provisioning triggers** (all three are adequate — this does not differentiate):
- Paddle: `transaction.completed` ("confirm payment and start fulfillment"), `subscription.created` ("save the customer and subscription IDs against your user, and grant access"), `subscription.activated`, `subscription.updated` ("catch-all... including renewals, upgrades, downgrades, pauses, resumes, and cancellations"), `subscription.past_due`, `subscription.canceled`, `transaction.payment_failed`; HMAC signature verification documented [B5].
- Stripe: `checkout.session.completed`, `invoice.paid` ("provision access... when you receive this event and the subscription status is active"), `invoice.payment_failed`, `customer.subscription.created/updated/deleted`; guidance: "when a subscription changes to `canceled` or `unpaid`, revoke access"; store an access-expiration timestamp updated on `invoice.paid` [B6].

### B2. MoR Comparison Table at EventCore Price Points

Fees from official pages [B1], [B2]; per-price computations are **interpretation** (£/$ ≈ 1.27, 50¢ ≈ £0.40; VAT-exclusive price).

| | Paddle (MoR) | Lemon Squeezy → Stripe Managed Payments (MoR) | Stripe direct (UK) |
|---|---|---|---|
| Headline fee | 5% + $0.50 [B1] | 5% + $0.50 [B3], [B4] | 1.5%–3.25% + 20p by card region + 0.7% Billing + 0.5% Tax Basic [B2] |
| **Cost on £29/mo** | **≈ £1.85 (6.4%)** | ≈ £1.85 (6.4%) | UK card ≈ £0.99 (3.4%); EEA ≈ £1.28 (4.4%); non-EEA ≈ £1.49 (5.1%) (+2% FX where converted) |
| **Cost on £79/mo** | **≈ £4.35 (5.5%)** | ≈ £4.35 (5.5%) | UK ≈ £1.99 (2.5%); EEA ≈ £2.92 (3.7%); non-EEA ≈ £3.66 (4.6%) |
| EU/global VAT & sales tax | **Paddle is liable**: registration, filing, remittance included [B1] | Stripe as MoR is liable [B3], [B4] | **You are liable**: non-Union OSS quarterly returns for EU B2C from first sale; per-country rates; Stripe Tax calculates only [B10], [B11], [B12], [B2] |
| Chargebacks/fraud | Included [B1] | Included (MoR) [B4] | Merchant handles (per-dispute fees) |
| Dunning/recovery | "Advanced churn recovery" incl.; 7 retries/30 days default, pause-or-cancel outcome [B1], [B8] | Stripe Smart Retries machinery [B7] | Smart Retries: "8 tries within 2 weeks" recommended default; outcome cancel / unpaid / past_due [B7] |
| Availability to a UK founder today | **GA, self-serve** [B1] | Invite-gated transition, "35+ countries", expanding "later in 2026" [B3] — Medium confidence | GA, self-serve [B2] |
| Provisioning webhooks | Full lifecycle event set [B5] | Stripe event set post-migration [B6] | Full lifecycle event set [B6] |

**Reading** *(interpretation)*: at 100 customers on £29, Paddle costs ~£85–90/mo more than Stripe direct — roughly one hour of accountant time, versus doing quarterly OSS returns across 27 member states with personal liability for mistakes. For a solo operator the MoR premium (~2.5–3 points of revenue) is the cheapest compliance outsourcing available. **Recommended: Paddle** — it is the only MoR that is GA and self-serve for a UK founder *today*, its dunning is built in, and its webhook set maps 1:1 onto the provisioning flow below. Revisit Stripe Managed Payments at public GA: same fee, and it would unify MoR + Stripe's superior API surface.

### B3. How Comparable Products Automate Provision-on-Payment

Three proven models, in decreasing similarity to EventCore's plan [B17], [B18]:

1. **Elestio (dedicated-VM-per-customer — EventCore's model)**: "you pick an app, pick a cloud provider (Hetzner, DigitalOcean, AWS, etc.), and they deploy it on a dedicated VM with SSL, backups, and auto-updates" at ~$17/mo per app [B17]. Subscription payment is the trigger; the platform orchestrates VM creation on the chosen cloud, DNS, TLS, and hands the customer a URL + admin credentials in a portal.
2. **PikaPods (shared-infra containers)**: "pick an app... click deploy, with your instance live in under a minute with a generated URL"; platform "handles the server, the updates, the backups, and the SSL certificates"; from ~$1.70/mo [B17], [B18]. Cheaper because pods share hosts — a later optimization for EventCore, not the launch model (single-tenant VM is the positioning).
3. **Cloudron (bring-your-own-VPS + license)**: customer installs the platform on their own Hetzner/DO box; the vendor only sells the license — infra billing stays with the customer [B17]. This is EventCore's *self-host* tier, not the managed tier.

**Standard tooling for the Hetzner variant** (all official docs): the orchestrator calls `POST https://api.hetzner.cloud/v1/servers` with `server_type`, `image`, and **`user_data`** containing a cloud-init config — "the script is automatically executed during boot, so when you log in for the first time, the system is already configured" (users created, firewall up, services started) [B13], [B14]. The same is expressible declaratively via the Terraform/OpenTofu `hcloud_server` resource (`user_data` field) [B15] or the Ansible `hetzner.hcloud.server` module [B16]. For a fleet of identical single-tenant instances, direct API + cloud-init from a small control-plane service is the least machinery; Terraform adds state-management overhead per customer that a solo operator doesn't need *(interpretation)*.

**Critical Hetzner billing fact for lifecycle design**: a powered-off cloud server **bills at full rate** — "you will still be billed while the server object exists even if it is powered off; to stop charges you must delete the server"; the pattern for dormant instances is "graceful shutdown, snapshot, delete, then restore from the snapshot when needed" (snapshots cost €0.0143/GB/mo, per `hosting-feasibility.md` §1b) [B19].

### B4. Recommended Payment → Provision → Credentials Flow

**Stack: Paddle Billing (MoR) → EventCore control plane (one small service + its own EventCore instance for auditability) → Hetzner Cloud API + cloud-init → transactional email + customer portal.**

1. **Checkout**: Customer buys the £29/£79 plan via Paddle checkout (overlay or hosted page) on the marketing site. Paddle, as merchant of record, applies the correct VAT per country, issues the invoice/receipt, and owns the tax filing [B1].
2. **Webhook intake**: Paddle sends `transaction.completed` + `subscription.created` to the control plane. The endpoint verifies the Paddle HMAC signature, dedupes by event ID (webhooks are retried — idempotency is mandatory), stores `customer_id`/`subscription_id` against the new tenant record, and returns 200 immediately; provisioning proceeds async [B5].
3. **Provision**: The orchestrator generates the tenant's secrets (admin credentials, EventCore API key, DB password), then calls Hetzner `POST /v1/servers` — `server_type: cx33` (per `hosting-feasibility.md` sizing), a maintained base snapshot (Ubuntu + Docker preinstalled), firewall ID, and `user_data` cloud-init that: creates the service user, writes the tenant's `.env`, runs `docker compose up` (EventCore + TimescaleDB + Caddy for automatic TLS), and enables the backup timer [B13], [B14]. In parallel it creates the DNS record `{tenant}.eventcore.host` via the DNS provider's API.
4. **Verify + deliver credentials**: The control plane polls the instance's health endpoint (readiness typically minutes on Hetzner-class provisioning [B17], [B18]); on green it marks the tenant active and sends a transactional email containing a **magic link to the customer portal** — the portal (not the email) shows the instance URL, admin credentials/API key, and status. On red after a timeout: alert the operator, auto-retry once, else fall to the manual runbook.
5. **Ongoing loop**: Paddle auto-renews monthly and emits `subscription.updated` / `subscription.past_due` / `subscription.canceled`; the control plane maps these to the lifecycle state machine in B5. Upgrades (£29→£79) arrive as `subscription.updated` with the new price — trigger a Hetzner rescale CX33→CX43 (minutes of planned downtime, customer-notified; note rescales reprice to current new-order rates, per `hosting-feasibility.md` [H1] — priced into tiers already).

Every step is a webhook-consumer + API-caller — no human in the loop; total new software is one control-plane service, a base image, and a cloud-init template. *(The control plane consuming its own Paddle webhooks through an EventCore instance is free dogfooding and a live demo.)*

### B5. Subscription Lifecycle Automation and Policy Recommendation

**Dunning mechanics available off the shelf:**
- Paddle: on failed renewal "the subscription status changes to past due and Payment Recovery... gets to work"; default "failed payments retry 7 times over 30 days before they're canceled"; recovery emails "days 1, 3, 5, 7"; end-of-window action configurable: **pause or cancel** — and "canceled subscriptions can't be reinstated" [B8], [B9].
- Stripe (for later reference): Smart Retries, "8 tries within 2 weeks" recommended, AI-timed; end state configurable: canceled / unpaid / past_due [B7].

**Recommended policy ladder** *(interpretation, built on the cited mechanics + Hetzner billing facts [B19] and the data-exit positioning in `market-positioning.md` gap #3)*:

| Day | Trigger | Action |
|---|---|---|
| 0 | `subscription.past_due` [B5] | **No service impact.** Paddle dunning runs (7 retries / 30 days, emails days 1/3/5/7 [B8]). An event log with ingest gaps is unrecoverable — suspending during dunning would destroy the product's core promise. Portal shows a payment banner. |
| 30 | Recovery window ends → configure Paddle to **pause**, never cancel ("canceled subscriptions can't be reinstated" [B8]) | **Suspend**: graceful shutdown → **snapshot → delete the VM** (a stopped Hetzner server bills at full rate [B19]). Snapshot ≈ €0.30–0.50/mo carrying cost. Email: service suspended, data safe, one-click export available. |
| 30–60 | Grace period | Data held as snapshot + the standing off-VM backup. Export/download offered throughout — the "data-exit button" is the answer to "what happens to my events?". Reactivation = customer fixes payment → `subscription.updated` (resumed) → restore server from snapshot, reattach DNS; minutes, automated. |
| 60 | Grace expiry, after 2 warning emails | **Delete** snapshot and backups; confirm deletion in writing. A defined retention-then-erasure schedule is also the GDPR-clean answer (storage limitation) and belongs in the DPA (`market-positioning.md` gap #4). |

**Upgrades/downgrades**: Paddle handles proration and emits `subscription.updated` [B5]. Infra side: upgrade = Hetzner rescale to the bigger type (disk grows permanently); **downgrade = CPU/RAM-only rescale, because Hetzner disks cannot shrink** — the customer keeps the larger disk, EventCore absorbs the pennies of difference rather than doing a risky snapshot-migration *(interpretation; verify current rescale constraints in the Hetzner console — see Knowledge Gaps)*.

## Knowledge Gaps

1. **Svix operator dashboard analytics**: Svix's public docs describe the consumer App Portal and recovery actions in detail, but not the operator dashboard's metrics/analytics screens. **Attempted**: docs.svix.com search (FAQ/message-tags/retention only), svix.com/features (404). **Recommendation**: sign up for the free tier and screenshot the operator dashboard before finalizing EventCore's metrics screen scope. Confidence of the Svix matrix row: Medium for operator side, High for portal.
2. **Stripe Managed Payments status/availability for UK merchants**: lemonsqueezy.com (403), lemonsqueezy.com/blog/2026-update (403), docs.stripe.com/managed-payments (404), stripe.com/payments/managed-payments (404) — circuit breaker applied after repeated failures. Status ("35+ countries", invite-gated, public access "very soon", 5% + $0.50) rests on search-engine summaries of the Lemon Squeezy blog and secondary coverage [B3], [B4]. **Recommendation**: check the Stripe dashboard/waitlist directly before committing; if Managed Payments is GA for UK merchants at decision time, re-run the B2 comparison.
3. **Paddle seller onboarding friction**: Paddle reviews/approves new sellers (website, product category) before go-live; approval timelines and rejection criteria were not researched. **Recommendation**: apply early — approval is on the critical path to first revenue. [unverified]
4. **Hetzner rescale/downgrade constraints**: "disk cannot shrink on rescale" is stated from operational knowledge, not fetched docs. **Recommendation**: verify in Hetzner docs/console before publishing tier-change policy.
5. **Convoy portal theming**: not documented in fetched pages; white-label gating inferred from the pricing page via `market-positioning.md` [E2]. Absence of documentation ≠ documented absence.
6. **WorkOS Admin Portal theming/branding**: explicitly none documented [W2]; whether branding exists at enterprise tiers is unknown.
7. **Paddle payout/FX fees to UK bank accounts**: the 5% + 50¢ headline may not include payout currency conversion; not itemized on the pricing page [B1]. **Recommendation**: confirm payout terms during onboarding.

## Conflicting Information

### Conflict 1: Is Lemon Squeezy a currently viable MoR choice?
**Position A**: Lemon Squeezy's own site still markets "payments, tax & subscriptions for software companies" (homepage title, via search) — implying open for business.
**Position B**: Lemon Squeezy's 2026 update (via search summary; direct fetch blocked) describes migration to Stripe Managed Payments as the successor, with Managed Payments "moving fast" toward public access — implying LS is in wind-down as a standalone MoR [B3].
**Assessment**: Position B is the operative truth for a *new* merchant decision: even if signups remain technically open, building on a platform mid-absorption is adopting a migration project on day one. Weight: choose Paddle now; re-evaluate Stripe Managed Payments at GA. Confidence: Medium (primary pages unfetchable).

## Full Citations

Accessed 2026-07-05.

**Part A — dashboards**
[S1] Svix. "Application Portal". https://docs.svix.com/app-portal
[S2] Svix. "Retries". https://docs.svix.com/retries
[S3] Svix. "FAQ". https://docs.svix.com/faq — via search summary (portal: add endpoints, pick event types, view logs, replay)
[H1] Hookdeck. "Documentation" (index). https://hookdeck.com/docs
[H2] Hookdeck. "Events". https://hookdeck.com/docs/events
[H3] Hookdeck. "Metrics". https://hookdeck.com/docs/metrics
[C1] Convoy. "Events and Event Deliveries". https://getconvoy.io/docs/product-manual/events-and-event-deliveries.md
[C2] Convoy. "Portal Links". https://getconvoy.io/docs/product-manual/portal-links
[C3] Convoy. Documentation index. https://getconvoy.io/docs/llms.txt (batch-retry API, metrics page paths)
[W1] WorkOS. "Audit Logs" (docs). https://workos.com/docs/audit-logs
[W2] WorkOS. "Admin Portal" (docs). https://workos.com/docs/admin-portal
[W3] WorkOS. "Audit Logs" (product page). https://workos.com/audit-logs

**Part B — billing/provisioning**
[B1] Paddle. "Pricing". https://www.paddle.com/pricing
[B2] Stripe. "Pricing" (UK). https://stripe.com/gb/pricing
[B3] Lemon Squeezy. "2026 Update: Lemon Squeezy + Stripe Managed Payments". https://www.lemonsqueezy.com/blog/2026-update — [Restricted Access: fetch 403; content via search summaries]
[B4] Fungies.io. "Lemon Squeezy Acquired by Stripe: What This Means for SaaS Founders in 2026". https://fungies.io/lemon-squeezy-stripe-acquisition-saas-founders-2026/ — [fetch 403; via search]; corroborates [B3]
[B5] Paddle. "Webhooks Overview". https://developer.paddle.com/webhooks/overview
[B6] Stripe. "Using webhooks with subscriptions". https://docs.stripe.com/billing/subscriptions/webhooks
[B7] Stripe. "Smart Retries". https://docs.stripe.com/billing/revenue-recovery/smart-retries
[B8] Paddle. "Subscription past due" (renewal & dunning). https://developer.paddle.com/build/lifecycle/subscription-renewal-dunning
[B9] Paddle. "Retain Payment Recovery: How It Works & Retry Cadence". https://www.paddle.com/help/profitwell-metrics/retain/how-it-works/retain-payment-recovery-how-it-works-retry-cadence — via search summary
[B10] European Union. "VAT One Stop Shop". https://europa.eu/youreurope/business/taxation/vat/one-stop-shop/index_en.htm
[B11] SimplyVAT. "Non-Union OSS". https://simplyvat.com/non-union-oss/ ; GoFile. "VAT on Digital Services (OSS)". https://gofile.co.uk/knowledgebase/vat/vat-on-digital-services/ — B2C-only scope, no-threshold rule
[B12] Stripe. "What is EU VAT & VAT OSS?". https://stripe.com/guides/introduction-to-eu-vat-and-european-vat-oss
[B13] Hetzner Community. "Basic Cloud Config" (cloud-init tutorial). https://community.hetzner.com/tutorials/basic-cloud-config/
[B14] Hetzner. "Hetzner Cloud API" (POST /v1/servers, user_data). https://docs.hetzner.cloud/
[B15] HashiCorp/Hetzner. "hcloud_server resource". https://registry.terraform.io/providers/hetznercloud/hcloud/latest/docs/resources/server.html
[B16] Ansible. "hetzner.hcloud.server module". https://docs.ansible.com/projects/ansible/latest/collections/hetzner/hcloud/server_module.html
[B17] vikasprogrammer. "I Compared 6 Platforms for Deploying Self-Hosted Apps in 2026". https://dev.to/vikasprogrammer/i-compared-6-platforms-for-deploying-self-hosted-apps-in-2026-3j8 — community, medium trust; corroborated by [B18] and prior research [E2]
[B18] PikaPods. "Instant Open Source App Hosting". https://www.pikapods.com/ — plus reviews via search (makerstack.co, linuxhandbook.com)
[B19] Hetzner. "Billing system at Hetzner". https://docs.hetzner.com/general/billing-and-account-management/billing-at-hetzner/billing-system-hetzner/ ; CloudTally. "Why Hetzner charges for stopped servers". https://cloudtally.eu/blog/why-hetzner-charges-for-stopped-servers — stopped servers bill until deleted

**Internal**
[E2] `docs/product/market-positioning.md` (2026-07-05) — Convoy $999 feature gates, Hookdeck no-self-host, Elestio baseline, competitor pricing
[E3] `docs/product/hosting-feasibility.md` (2026-07-05) — CX33 sizing, snapshot pricing, rescale repricing [H1], rotation ladder, margin model

## Research Metadata

Web operations: 22 (14 fetches — 4 failed with 403/404, all recovered via alternate sources or logged as gaps; 8 searches). Sources examined ~40; cited 31 (29 external + 2 internal). Cross-verification: all dashboard capabilities from vendors' own official docs (authoritative for "what the vendor's UI does"); MoR fees from official pricing pages; Lemon Squeezy/Managed Payments status search-corroborated only (Medium); VAT/OSS rules triangulated across EU official + two UK tax advisories + Stripe guide; Hetzner stopped-server billing from official docs + independent explainer. Confidence distribution: High ~65% (official docs), Medium ~30% (search-summarized pages, LS status, sizing interpretations), Low ~5% (Paddle onboarding, payout FX). Circuit breaker invoked once (Stripe Managed Payments/Lemon Squeezy primary pages). Output: `docs/product/dashboard-and-billing-research.md`. No other files modified.
