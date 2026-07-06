# Security

## Supported versions

EventCore is pre-1.0 and ships from `main`. Security fixes land on `main`; run
the latest commit. There are no back-ported release branches yet — when 1.0
tags arrive, this section will name the versions that receive fixes.

## Reporting a vulnerability

Report privately through GitHub's **Private Vulnerability Reporting**: the
repository's **Security** tab → **Report a vulnerability**. **Do not open a
public issue, PR, or discussion** for anything security-relevant — that
discloses it to everyone before there is a fix.

Tell us what you found, how to reproduce it, and the impact you see. We will
acknowledge the report, work the fix privately, and credit you when it ships
unless you'd rather stay anonymous.

## Security model

EventCore is **self-hosted**: you run the container, so your deployment's
network exposure, TLS termination, and database credentials are yours to own —
put it behind a TLS-terminating proxy and don't expose the raw port to the
public internet. Within that boundary, the service holds these guarantees:

- **API keys are hashed at rest and shown once.** Issuing a key returns the
  plaintext (`ek_…`, 32 bytes from `SecureRandom`) exactly once, in that one
  response; only its SHA-256 hash is stored (`security/ApiKeyStore`). We can't
  recover a lost key — issue a new one and revoke the old. Revocation takes
  effect immediately (the record is kept for audit; authentication checks
  `revoked_at IS NULL`).
- **Webhook bodies are signed; receivers must verify.** Every delivery carries
  `X-EventCore-Signature: sha256=<hex>` — HMAC-SHA256 over the exact request
  body with the per-subscription secret (`crypto/HmacSha256`,
  `deliveries/PendingDelivery`). Recompute it and reject on mismatch before
  trusting the payload; compare in constant time. Worked example:
  [`examples/spring-boot-demo/.../EventCoreWebhookReceiver.java`](examples/spring-boot-demo/src/main/java/dev/eventcore/examples/orders/EventCoreWebhookReceiver.java),
  and `openssl` one-liner in [`scripts/walkthrough.sh`](scripts/walkthrough.sh).
- **Key management is admin-gated.** Issuing and revoking keys requires
  `X-Admin-Token` (`security/SecurityProperties`, constant-time compared);
  those endpoints reject everything while `ADMIN_TOKEN` is unset. Tenant routes
  use `X-API-Key`; `/health` and `/metrics` are intentionally public.

## Contributing security fixes

Security patches follow the same house method as any change — a failing
integration test first, then the fix — described in
[docs/development.md](docs/development.md). Coordinate the disclosure privately
before opening the PR.
