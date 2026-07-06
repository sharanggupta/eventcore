<!--
Thanks for contributing to EventCore. Keep this PR focused on one change.
The full "how to add a feature" method lives in docs/development.md — this
template is just the checklist that catches EventCore's usual footguns.
-->

## What & why

<!--
One or two sentences: what this changes and the problem it solves. Link the
issue for the rest. If behavior changed at the API surface, show the before/
after (a curl and its response) — reviewers verify against the real shape.
-->

-

## Checklist

Delete any line that genuinely doesn't apply (a docs-only or dashboard-only PR
won't touch migrations); leave the rest checked so a reviewer can trust them.

- [ ] **Integration test written first.** New behavior has a test extending
      `dev.eventcore.IntegrationTestBase` — real Spring context, the shared
      Testcontainers TimescaleDB, HTTP through `api()`. Test names read as
      sentences (`aRevokedKeyStopsAuthenticating`).
- [ ] **Migrations are append-only.** Any schema change is a *new*
      `V<N>__<what>.sql` in `backend/src/main/resources/db/migration` (next is
      **V17**) — no already-applied `V<N>` file was edited.
- [ ] **`cd backend && ./mvnw verify` passes** locally — the same command CI
      runs, the full suite against a real database, no mocks.
- [ ] **Swagger is current.** New endpoints have an `@Operation(summary = ...)`
      and their controller a `@Tag`; the all-vs-list filter shape still goes
      through `api.Wildcards`.
- [ ] **Errors stay in shape.** Every new failure path returns
      `{"error": "<what went wrong>"}` with the right 400/401/404/409 —
      thrown as an `api` exception, never a hand-built response (see
      `ApiExceptionHandler`).
- [ ] **Docs track behavior.** If the API changed, the README API section and
      the [walkthrough](../docs/walkthrough.md) (and `scripts/walkthrough.sh`
      if it asserts the path) are updated.

<!-- New to the codebase, or unsure any box applies? docs/development.md walks
the house method end to end — this list is its short form. -->

Fixes #
