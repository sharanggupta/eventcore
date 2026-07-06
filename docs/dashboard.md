# The dashboard

A tenant-scoped web UI for one EventCore instance: see the pipeline, read the
log, diagnose and recover deliveries, and watch pull consumers — without curl.

One dashboard per instance. It renders on the server and calls your instance
with the API key from `.env.local`; the key never reaches the browser, and
the instance needs no CORS setup.

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
- **Time range** (Kibana-style): preset chips — Last 15m / 1h / 24h / 7d /
  All time — or a custom from → to (UTC) range. URL-driven and composable
  with the type filter and pagination.
- **Click a row** to expand the full payload as pretty-printed JSON.
- **Filter** by exact type with the input top-right (URL-driven:
  `/events?type=invoice.paid` is shareable).
- **Payload search**: give a payload field and value to find every event
  whose payload carries it — `userId` + `u_123` finds `{"userId": "u_123"}`.
  Dotted fields walk into nested objects (`order.id` matches
  `{"order": {"id": ...}}`), numbers match by their text form
  (`amountCents` + `4900`), and it composes with the type filter, time
  range, and pagination.
- **Paginate** with `Older ⟶` / `⟵ Newest` — cursor-based, no page drift.

### Deliveries `/deliveries`
The outbox with status tabs (all / pending / delivered / failed) and the same
time-range chips as Events. While any visible delivery is pending, the page
**auto-refreshes every 3 seconds** — a retried delivery flips from pending to
delivered before your eyes, no reload. Click any delivery for its
**per-attempt timeline**: attempt number, status code or transport error,
response snippet, duration, timestamp — and while the delivery is pending,
the detail page refreshes on the same cadence, so new attempts appear in the
timeline as they happen. Every failed delivery has a **Retry** button right
on its list row (plus **Redeliver now** on the detail page), and the failed
tab has **Redeliver all failed** for whole-outage recovery. Both requeue
through the same API the curl examples use — the always-running dispatcher
picks requeued deliveries up within its poll interval (1s by default), so
there is nothing to "trigger": recovery is a state change, not a manual
dispatch.

### Webhooks `/webhooks`
Manage subscriptions without curl: **register** (URL, optional event-type
filter and payload-field allow-list) — the signing secret is revealed exactly
once, in the UI, right after creation; **edit filters** changes a
subscription's event types and payload allow-list in place (same id, same
secret — PATCH under the hood); **delete** removes a subscription and its
delivery history. The list shows each endpoint's filters at a glance.

### Consumers `/consumers`
Every pull subscription with its **lag** (events not yet committed past,
respecting the consumer's type filter), its position (`beginning`, or the
timestamp it has committed up to), and its event-type filter. A lag of zero
is green; a growing number names the stuck consumer before it matters.

## Production build

```bash
cd dashboard
npm run build && npm run start   # or containerize
```

Either way, set the same two variables `.env.local` carries: `EVENTCORE_URL`
(defaults to `http://localhost:8080`) and `EVENTCORE_API_KEY`.
