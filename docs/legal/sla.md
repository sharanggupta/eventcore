# Service Level Agreement — DRAFT

> **Status: draft** for the managed offering; takes effect at launch. The
> live status page and incident process land with the hosting decision
> (tracked in the provisioning milestone). Self-hosted instances set their own
> availability targets — the [monitoring docs](../../README.md#monitoring)
> give you the alerts to do it.

## Commitment

**99.9% monthly uptime** per customer instance — measured as successful
responses to `GET /health` probed externally every 60 seconds, excluding
scheduled maintenance (announced ≥48h ahead, ≤2h/month, off-peak EU time).

Honesty note: this is a single-node service by design (that is what makes it
affordable and single-tenant). 99.9% (≤43.8 minutes downtime/month) is what a
well-operated single node with tested restores genuinely sustains. We publish
this number rather than a five-nines fiction.

## Credits

| Monthly uptime | Credit (of that month's fee) |
|---|---|
| < 99.9% | 10% |
| < 99.5% | 25% |
| < 99.0% | 50% |

Claimed by email within 30 days; applied to the next invoice.

## What counts as downtime

The instance failing external health probes. Not counted: your consumers'
endpoints failing (deliveries retry and are recoverable — that is the
product), scheduled maintenance, force majeure, suspension for non-payment
(which itself follows a 30-day dunning grace).

## Data durability

Daily off-instance backups with restore drills; the export bundle
(`scripts/export.sh` semantics) is available to customers on request at any
time — your data is yours, including on the way out.

## Support

[CHANNEL — email] with first response within 1 business day (EU hours).
Incidents are posted to the status page [URL — pending] as they happen.
