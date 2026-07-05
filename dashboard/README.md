# EventCore Dashboard

The tenant dashboard: a Next.js app that reads one EventCore instance and
shows the pipeline — overview charts, the event log, delivery operations
(including one-click redelivery), and the pull-consumer fleet.

Server-rendered by design: your API key lives in `.env.local` on the server
and is never sent to the browser, so the instance needs no CORS configuration
and tenancy comes from the deployment (one dashboard per instance).

## Run it

```bash
# 1. have an instance running (from the repo root: docker compose up -d)
#    and an API key (see the root README quick start)

# 2. configure
cd dashboard
cp .env.local.example .env.local     # then paste your EVENTCORE_API_KEY

# 3. run
npm install
npm run dev                          # http://localhost:3000
```

## Screens

| Route | What it shows |
|---|---|
| `/` | Overview: stat cards, delivery-status donut, ingest-per-hour bars, attempt outcomes, per-type flow health |
| `/events` | The log, newest first: filter by type, click a row for the full payload, page older with the cursor |
| `/deliveries` | Outbox by status tab; click through to the per-attempt timeline; **Redeliver now** on failed deliveries |
| `/consumers` | Pull-consumer fleet with per-consumer lag — the screen no competitor has |

Full user guide: [docs/dashboard.md](../docs/dashboard.md).
