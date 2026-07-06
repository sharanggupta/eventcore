# Integration examples

Two tiny, complete applications that talk to a running EventCore. Start the
stack first and issue yourself a key:

```bash
docker compose up --build -d      # from the repo root
KEY=$(curl -s -X POST http://localhost:8080/v1/api-keys \
  -H 'X-Admin-Token: local-admin-token' -H 'Content-Type: application/json' \
  -d '{"name": "examples"}' | jq -r .key)
```

## Python pull consumer (`python-pull-consumer/`)

One file, standard library only. Creates a named durable cursor and runs the
crash-safe fetch → process → commit loop — backfills all history, then tails.

```bash
EVENTCORE_API_KEY=$KEY python3 examples/python-pull-consumer/consumer.py my-consumer beginning
```

Kill it (Ctrl-C) and re-run: it resumes from its committed position. Post an
event in another terminal and watch it arrive:

```bash
curl -s -X POST http://localhost:8080/v1/events -H "X-API-Key: $KEY" \
  -H 'Content-Type: application/json' -d '{"type": "demo.ping", "payload": {"n": 1}}'
```

## Spring Boot "orders service" (`spring-boot-demo/`)

A demo business service showing both integration directions:

- **Publishing**: `POST /orders {"item": "..."}` places an order and records
  an `order.placed` event in EventCore (see `EventCoreClient` — it is one POST).
- **Receiving**: `/webhooks/eventcore` accepts EventCore deliveries and
  **verifies the HMAC signature before trusting the body**
  (see `EventCoreWebhookReceiver` — copy this class into your own service).

Run it (port 8081), then register its webhook and wire the secret:

```bash
# 1. register the webhook; EventCore (in Docker) reaches your app via host.docker.internal
#    (optional: add "payloadFields": ["orderId"] to the body to deliver only
#    those payload fields; change it later with PATCH /v1/webhooks/{id})
WEBHOOK=$(curl -s -X POST http://localhost:8080/v1/webhooks -H "X-API-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d '{"url": "http://host.docker.internal:8081/webhooks/eventcore", "eventTypes": ["order.placed"]}')
SECRET=$(echo "$WEBHOOK" | jq -r .secret)

# 2. start the demo with its credentials
EVENTCORE_API_KEY=$KEY EVENTCORE_WEBHOOK_SECRET=$SECRET \
  backend/mvnw -f examples/spring-boot-demo/pom.xml spring-boot:run

# 3. place an order - watch the demo log "verified delivery: {...order.placed...}"
curl -s -X POST http://localhost:8081/orders \
  -H 'Content-Type: application/json' -d '{"item": "rubber duck"}'
```

The full round trip: your request → the demo emits `order.placed` → EventCore
stores it durably → EventCore delivers it back to the demo, signed → the demo
verifies and processes it. Query the record any time:

```bash
curl -s -H "X-API-Key: $KEY" 'http://localhost:8080/v1/events?type=order.placed' | jq .
```

That returns every `order.placed` event. To pinpoint one order, match on any
payload field with `payload.<field>=<value>` — dotted paths reach nested
fields, and multiple `payload.` params AND together:

```bash
curl -s -H "X-API-Key: $KEY" \
  'http://localhost:8080/v1/events?type=order.placed&payload.item=rubber%20duck' | jq .
```
