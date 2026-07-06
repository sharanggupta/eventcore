# Contributing to EventCore

Thanks for being here. EventCore is a small, opinionated system, and the
fastest way to land a change is to build it the way the existing features were
built. This page is the front door: what to work on, how to get running in
five minutes, and the handful of house rules that keep the codebase coherent.
The *how-to* — the codebase map and the step-by-step method — lives in the
[developer guide](docs/development.md), which is the single source of truth;
this page links to it rather than repeating it.

## Ways to contribute

Issues, docs, and code all count. Bug reports and reproductions are genuinely
useful — a failing `curl` against `docker compose up` is the best kind. Doc
fixes (a stale command, a clearer sentence in the [walkthrough](docs/walkthrough.md)
or a `docs/` guide) are welcome as direct PRs. And code: a new query filter, a
delivery-ops endpoint, a dashboard screen — the [developer guide](docs/development.md)
shows exactly how the last one was added.

## Before you start

For a **feature or a behavior change**, open an issue first so we can agree on
the shape before you write it — it saves a rewrite. For a **bug fix or a doc
change**, skip straight to a PR.

Either way, read two things first: the [codebase map and the "adding a feature"
method](docs/development.md#the-map) in the developer guide (it walks the
package layout, the request lifecycle, and the red-green house method), and the
[testing guide](docs/testing/README.md) for how the suite is structured and run.

## Local setup (five minutes)

Prerequisites: Docker with Compose, Java 21, and `curl` + `jq` for the
walkthrough. Three commands from a fresh clone:

```bash
docker compose up --build -d          # backend on :8080 + TimescaleDB (from the repo root)
cd backend && ./mvnw test             # the full integration suite (~1 min, Docker required)
./scripts/walkthrough.sh              # assert the whole product end-to-end (from the repo root)
```

Success signals, in order:

- `curl http://localhost:8080/health` returns `OK`.
- `./mvnw test` ends with `Tests run: N, Failures: 0, Errors: 0`.
- `./scripts/walkthrough.sh` ends with `All checks passed.`

Want a screen for what you're building? The dashboard runs separately:
`cd dashboard && npm install && npm run dev` serves the UI at
`http://localhost:3000` (it needs `EVENTCORE_API_KEY` in `dashboard/.env.local`;
see the [dashboard guide](docs/dashboard.md)).

## House rules

These are the conventions that make a change look like it belongs. The
[developer guide](docs/development.md#adding-a-feature-the-house-method) is the
full method; this is the checklist to keep next to you.

- **Integration-test-first.** Write the failing test before the code, and
  extend `dev.eventcore.IntegrationTestBase` — it gives you an authenticated
  `api()` client and one shared Testcontainers TimescaleDB. Boot the real
  context and talk HTTP; we don't mock the database. Test names read as
  sentences (`aRevokedKeyStopsAuthenticating`).
- **Migrations are append-only.** Add the next `V<N>__<what>.sql` in
  [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration)
  — the next number is **V17**. Never edit a migration that has already been
  applied; correct it forward with a new one.
- **Records are the contract.** Request/response types are `record`s named
  `*Request` / `*Response`, and input validation lives on the request itself
  (`request.validate()`). No `Map<String, Object>` in an API contract.
- **Naming says where the code lives.** `*Controller` is the thin HTTP surface
  (validate, delegate, return a record); `*Store` / `*Outbox` owns the SQL for
  its tables via `JdbcClient` — there is no ORM, and keyset pagination
  everywhere means no `OFFSET`.
- **Reuse the shared `api` primitives.** `api.Wildcards` owns the all-vs-list
  filter mapping (`null` ↔ `[*]`), `api.Cursor` encodes keyset pages,
  `api.TimeBounds` parses `from`/`to`, and every error path returns
  `{"error": "<what went wrong>"}` by throwing an `api` exception — controllers
  never build error responses. A new status code needs a handler entry in
  `api.ApiExceptionHandler`.
- **Keep dependencies pointing inward.** Packages are by feature and the
  cross-package edges are deliberate (see the [component diagram](docs/architecture.md#backend-components)).
  When one capability needs another, own a port and have the other side
  implement it — the way `events` owns `events.EventSink` and depends on no
  sibling — rather than importing a sibling capability directly.

## Code style

The backend follows **Google Java Format** conventions — 4-space continuation
indents, ordered imports, no wildcard imports. There is no formatter wired into
the build yet, so match the surrounding style and keep your diff to the change
itself: no drive-by reformatting of untouched lines.

## PR expectations

- **`cd backend && ./mvnw verify` is green.** That is exactly what CI runs on
  every push and PR ([.github/workflows/ci.yml](.github/workflows/ci.yml)) — the
  full Testcontainers suite against a real database, no mocks.
- **One logical change per PR.** Easier to review, easier to revert.
- **Keep the docs in step with behavior.** If you change or add an endpoint,
  update the [README API section](README.md#api) and, when the behavior is
  worth showing, add or adjust a step in
  [`scripts/walkthrough.sh`](scripts/walkthrough.sh) and the
  [walkthrough](docs/walkthrough.md) — those outputs are real captured runs, so
  they can't drift.

EventCore is Apache-2.0 licensed; by contributing you agree your work is
released under the same [license](LICENSE).
