# Data Processing Agreement — TEMPLATE

> **Status: draft template.** For the managed offering, once launched. Not
> legal advice; have it reviewed before first use. Placeholders in [BRACKETS].
> Self-hosted users do not need this document — no processor relationship
> exists when you run EventCore yourself.

**Parties.** [OPERATOR LEGAL NAME] ("Processor") and the customer agreeing to
the managed service terms ("Controller").

**1. Subject matter.** Processor operates a dedicated EventCore instance on
behalf of Controller: receiving, storing, and delivering event records that
Controller's systems submit.

**2. Nature of the data.** Event payloads are defined entirely by Controller.
Controller warrants it has a lawful basis for any personal data it submits.
Processor does not inspect payload content except as needed for support with
Controller's consent.

**3. Duration.** The subscription term, plus the export grace period
([30] days after termination), after which all instance data is deleted.

**4. Processor obligations.** Process only on Controller's documented
instructions (the service's API calls and configuration are the instructions);
confidentiality for any personnel; security measures per Annex II; assist with
data-subject requests via the query, retention, and export features; notify
Controller of personal-data breaches without undue delay; delete or return all
data at termination (the export bundle constitutes return).

**5. Subprocessors.** Listed in [subprocessors.md](subprocessors.md).
Processor gives [30] days' notice before adding one; Controller may object by
terminating with a pro-rated refund.

**6. Data location.** All instance data resides in the EU
([Hetzner, Germany/Finland — pending final hosting decision]).

**7. Audit.** Processor provides the information reasonably necessary to
demonstrate compliance (this document, the subprocessor list, security
documentation, and export samples).

**Annex I — Processing details.** Categories of data subjects and personal
data: as determined by Controller's payloads. Processing operations: storage,
querying, webhook delivery, retention rotation, backup, export.

**Annex II — Technical and organisational measures.** Single-tenant instance
per customer; API-key authentication (keys hashed at rest); admin-token-gated
key management; HMAC-signed deliveries; configurable retention; encrypted
transport (TLS); off-instance backups [FREQUENCY]; access limited to
[OPERATOR] personnel.
