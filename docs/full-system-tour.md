# The full-system tour

Run everything together and watch one order flow through the whole system:
the **orders demo app** places an order → **EventCore** records and delivers
the event → the demo **receives it back, signature-verified** → the
**dashboard** shows all of it. Three terminals, ~10 minutes.

Prerequisites: Docker with Compose, Java 21, Node 20+, `curl`, `jq`.
Diagrams of what you are about to run: [architecture.md](architecture.md).

## Terminal 1 — the backend

```bash
git clone git@github.com:sharanggupta/eventcore.git && cd eventcore
docker compose up --build -d
```

Wait for the containers, then confirm it is alive:

```bash
curl http://localhost:8080/health
```

> `OK`

Issue an API key (shown once — we keep it in a variable and reuse it for
every component):

```bash
KEY=$(curl -s -X POST http://localhost:8080/v1/api-keys \
  -H 'X-Admin-Token: local-admin-token' -H 'Content-Type: application/json' \
  -d '{"name": "tour"}' | jq -r .key)
echo "$KEY"
```

> `ek_...` (43 characters)

## Terminal 1 — register the demo app's webhook

The demo app will listen on port 8081; EventCore (inside Docker) reaches it
via `host.docker.internal`. Register the endpoint and capture its signing
secret:

```bash
WEBHOOK=$(curl -s -X POST http://localhost:8080/v1/webhooks \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"url": "http://host.docker.internal:8081/webhooks/eventcore", "eventTypes": ["order.placed"]}')
SECRET=$(echo "$WEBHOOK" | jq -r .secret)
echo "$WEBHOOK" | jq '{eventTypes, secret: (.secret[0:12] + "...")}'
```

> `{"eventTypes": ["order.placed"], "secret": "whsec_..."}`

## Terminal 2 — the orders demo app

A tiny Spring Boot "business service" that publishes events to EventCore and
receives them back as signed webhooks
([source](../examples/spring-boot-demo) — ~150 lines, built to be copied):

```bash
cd eventcore   # the repo root
EVENTCORE_API_KEY=$KEY EVENTCORE_WEBHOOK_SECRET=$SECRET \
  backend/mvnw -f examples/spring-boot-demo/pom.xml spring-boot:run
```

(`$KEY` and `$SECRET` are from terminal 1 — paste the values if this is a
separate shell.) Wait for `Started OrdersDemoApplication` on port **8081**.

## Terminal 3 — the dashboard

```bash
cd eventcore/dashboard
cp .env.local.example .env.local
# edit .env.local: paste the same EVENTCORE_API_KEY=ek_...
npm install
npm run dev
```

Open **http://localhost:3000** — the Overview loads with zeros (or your
existing data). Keep it visible.

## Now make something happen

Place an order against the demo app (any terminal):

```bash
curl -s -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' -d '{"item": "rubber duck"}' | jq .
```

> `{"orderId": "ord_xxxxxxxx", "item": "rubber duck"}`

Within a second or two, three things happen — watch each:

**1. Terminal 2 (the demo) prints the verified round trip** — EventCore
delivered the event back and the demo checked the HMAC before trusting it:

> `verified delivery: {"id": "...", "time": "...", "type": "order.placed", "payload": {"item": "rubber duck", "orderId": "ord_..."}}`

**2. The dashboard shows the event.** Go to **Events**: a new `order.placed`
row sits at the top (newest first). **Click the row** — the full payload
expands as JSON. Try the filter box with `order.placed`; the URL becomes
shareable (`/events?type=order.placed`).

**3. The dashboard shows the delivery.** Go to **Deliveries**: a `delivered`
row with 1 attempt. Click it — the attempt timeline shows the `200`, the
response time, and the timestamp.

Place a few more orders and watch the **Overview** move: the events counter,
the delivery donut, and the ingest-per-hour bar chart all update on refresh.

## Bonus round 1 — watch a failure and fix it from the UI

Stop the demo app (Ctrl-C in terminal 2), then place an order via the API
directly:

```bash
curl -s -o /dev/null -X POST http://localhost:8080/v1/events \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"type": "order.placed", "payload": {"orderId": "ord_doomed", "item": "anvil"}}'
```

The dispatcher retries 5 times with exponential backoff (5s, 10s, 20s, 40s —
about 80 seconds total). On the dashboard's **Deliveries → failed** tab the
delivery appears with `attempts: 5`; click it to read each attempt's
connection error. Now restart the demo app (terminal 2), click
**Redeliver now** — the status flips to `pending`, the dispatcher retries,
and attempt 6 lands with a `200`. That is the whole incident-recovery story,
without a database shell.

## Bonus round 2 — a pull consumer backfills everything

While the webhook path pushes, a pull consumer walks the log at its own pace
— including everything that happened **before it existed**:

```bash
EVENTCORE_API_KEY=$KEY python3 examples/python-pull-consumer/consumer.py replayer beginning
```

> `created pull subscription 'replayer' from beginning` followed by every
> event so far, then `committed N events`, then it tails quietly.

Check the dashboard's **Consumers** tab: `replayer` appears with lag 0.
Ctrl-C the consumer, place two orders, and the tab shows lag 2 — restart the
consumer and it processes exactly those two.

## Tearing down

```bash
docker compose down        # keep the data
docker compose down -v     # or wipe it
```

## Where next

- [Walkthrough](walkthrough.md) — the same ground API-first with captured responses
- [Testing guide](testing/README.md) — the 92-test suite and the asserting e2e script
- [Dashboard guide](dashboard.md) — every screen explained
- [Developer guide](development.md) — add your own feature the house way
