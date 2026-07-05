# The dashboard

A tenant-scoped web UI for one EventCore instance: see the pipeline, read the
log, diagnose and recover deliveries, and watch pull consumers — without curl.

![architecture] One dashboard per instance. It renders on the server and calls
your instance with the API key from `.env.local`; the key never reaches the
browser, and the instance needs no CORS setup.

## Run it

```bash
# with the instance up (docker compose up -d from the repo root) and a key issued:
cd dashboard
cp .env.local.example .env.local   # paste your EVENTCORE_API_KEY
npm install
npm run dev                        # http://localhost:3000
```

No key configured? The Overview shows setup instructions instead of erroring.

## Screens

### Overview `/`
Stat cards (events in the log, delivered, dead-lettered, oldest-pending age),
a delivery-status donut, an ingest-per-hour bar chart (derived from the latest
200 events), attempt outcomes, and **flow health** — when each event type last
arrived, the UI twin of the `EventFlowStopped` alert.

### Events `/events`
The log, newest first, one event per row (UTC timestamp · type badge · id).
- **Click a row** to expand the full payload as pretty-printed JSON.
- **Filter** by exact type with the input top-right (URL-driven:
  `/events?type=invoice.paid` is shareable).
- **Paginate** with `Older ⟶` / `⟵ Newest` — cursor-based, no page drift.

### Deliveries `/deliveries`
The outbox with status tabs (all / pending / delivered / failed). Click any
delivery for its **per-attempt timeline**: attempt number, status code or
transport error, response snippet, duration, timestamp. Failed deliveries have
a **Redeliver now** button, and the failed tab has **Redeliver all failed**
for whole-outage recovery. Both requeue through the same API the curl
examples use — the always-running dispatcher picks requeued deliveries up
within its poll interval (1s by default), so there is nothing to "trigger":
recovery is a state change, not a manual dispatch.

### Webhooks `/webhooks`
Manage subscriptions without curl: **register** (URL, optional event-type
filter and payload-field allow-list) — the signing secret is revealed exactly
once, in the UI, right after creation; **delete** removes a subscription and
its delivery history. The list shows each endpoint's filters at a glance.

### Consumers `/consumers`
Every pull subscription with its **lag** (events not yet committed past,
respecting the consumer's type filter), position, and filters. A lag of zero
is green; a growing number names the stuck consumer before it matters.

## Production build

```bash
npm run build && npm run start   # or containerize; set the two env vars
```
