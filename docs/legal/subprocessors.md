# Subprocessors — TEMPLATE

> **Status: draft.** Final list depends on the managed-hosting launch
> decisions (hosting model, payment provider). Self-hosted instances have no
> subprocessors — that is rather the point.

| Subprocessor | Purpose | Data touched | Location | Status |
|---|---|---|---|---|
| Hetzner Online GmbH | Instance hosting, backups | All instance data (encrypted at rest) | Germany / Finland (EU) | pending launch |
| Paddle.com Market Ltd | Merchant of record: payments, invoicing, tax | Billing identity and payment data (never event data) | UK/EU | pending launch — see [dashboard-and-billing-research](../product/dashboard-and-billing-research.md) |
| GitHub, Inc. | Source code hosting, CI | Code only — never customer or event data | US | in use (open-source development) |

Changes to this list will be announced [30] days in advance via
[CHANNEL — e.g. the status page and email].
