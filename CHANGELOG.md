# Changelog

All notable changes to EventCore are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and EventCore aims to
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html): once a
version is tagged, a breaking change to the wire contract or the database schema
bumps the major.

Entries are written for the person doing the upgrade — what changes for a
client, a webhook receiver, a pull consumer, or an operator. How these changes
were made (test-first, append-only migrations, the exception-to-status
mapping) is not repeated here; that method lives once in
[docs/development.md](docs/development.md).

## [Unreleased]

A correctness-and-completeness pass. Two entries change the wire contract in
ways a client can see; two are schema migrations (`V15`, `V16`) that apply on
the next `docker compose up --build -d` with no action on your part; the rest
are internal. Nothing here removes an endpoint or renames a field.

### Added

- **Delivery redelivery cycles carry their own baseline.** Each webhook
  delivery now records the attempt count at which its current retry cycle began
  (`cycle_start_attempts`, migration `V15`), so exponential backoff is computed
  from that baseline rather than inferred from the live config. See the fix
  below for why this matters.

### Changed

- **Filter fields are always present, as `["*"]` or an explicit list — never
  `null` and never omitted.** The event-type and payload-field filters on
  webhook and pull subscriptions (`eventTypes`, `payloadFields`) now appear on
  every response with one of two shapes: `["*"]` means "all types" / "the full
  payload", and a specific list means exactly those. A client no longer has to
  treat a missing or null field as an implicit "all" — the meaning is on the
  wire. On the request side the encodings are interchangeable: absent, empty, or
  `["*"]` all mean "everything"; mixing `"*"` with specific values is rejected.
  One class, [`api.Wildcards`](backend/src/main/java/dev/eventcore/api/Wildcards.java),
  owns this all-vs-list mapping end to end.

  *Upgrade note:* if your code checks `if (eventTypes == null)` to detect
  "all types", switch to comparing against `["*"]`. The field is now guaranteed
  present.

- **Every error response is `{"error": "<what went wrong>"}` — framework errors
  included.** The documented error shape used to cover only the domain
  exceptions (validation, auth, not-found, conflict). It now also covers the
  errors Spring raises before your handler runs — a malformed JSON body, a bad
  path or query parameter, an unknown route, a wrong method, an unsupported
  media type — plus a catch-all so an unexpected failure returns
  `500 {"error": "internal error"}` instead of leaking a raw framework
  `ProblemDetail`. If you parse error bodies, you can now rely on the `error`
  key being there on **every** non-2xx response, at 400/401/404/405/409/415/500.

- **Pull fleet position reads as `"beginning"` or a timestamp, never `null`.**
  The fleet view (`GET /v1/pull-subscriptions`) reports where each consumer sits
  in the log as either the literal string `"beginning"` or the ISO-8601
  timestamp it has committed up to. This mirrors the vocabulary the create and
  rewind endpoints already accept for `from`/`to` (`"beginning"`, `"now"`, or a
  timestamp), so the value you read back is one you can hand straight to a
  rewind.

### Fixed

- **Retry backoff can no longer go negative when you lower `max-attempts`.**
  Backoff resets each redelivery cycle; the exponent is `attempts - cycle_start`.
  Previously the cycle start was derived from the give-up budget minus the live
  `max-attempts`, so lowering `eventcore.webhooks.max-attempts` while deliveries
  were mid-flight produced a negative exponent and a backoff *shorter* than the
  base interval — a burst of premature retries. The cycle baseline is now stored
  per delivery (`V15`, above), making backoff independent of live config.

- **A requeue that forgets its retry budget now fails fast.** The
  `gives_up_after` column no longer carries a database default (migration
  `V16`). The application already sets the budget explicitly on every insert
  (`max-attempts` on enqueue, `attempts + max-attempts` on requeue), so the
  old default was dead and duplicated the `eventcore.webhooks.max-attempts`
  config. Dropping it makes the application the single source of truth: an
  insert that omits the budget errors immediately instead of silently defaulting
  to a stale value.

- **Payload minimization never drops a full payload.** Deliveries snapshot the
  subscription's minimized view of the event body; a subscription with no
  `payloadFields` allow-list now reliably snapshots the whole payload rather
  than an empty one.

### Internal

These change no request, response, or database column — they are noted only so
the diff is legible to contributors reading [docs/development.md](docs/development.md).

- **The `events` package depends on no other capability.** Fan-out to the
  delivery outbox now goes through an outbound port,
  [`events.EventSink`](backend/src/main/java/dev/eventcore/events/EventSink.java),
  implemented in `deliveries`. The dependency points inward toward the
  contract, breaking the former `events → deliveries` cycle and keeping the
  package graph acyclic.
- **Pull subscriptions split their domain read-model from their wire response**,
  and the "where in the log" vocabulary is centralized in one place
  (`pull.LogPositions`) so the starting cursor is resolved once.
- **Dashboard writes go through a single typed request boundary**, shared test
  fixtures moved up into `IntegrationTestBase`, and the TimescaleDB image is
  pinned (`timescale/timescaledb:2.27.0-pg16`) so every run — CI, local, and
  the test suite — builds against the same database.

[Unreleased]: https://github.com/sharanggupta/eventcore/commits/main
