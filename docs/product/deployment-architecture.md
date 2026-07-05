# Deployment architecture: how the managed offering actually runs

Decision record for the founder's question: *"Does it need to be a separate
instance per user? Can the same instance not serve all users, and we scale
horizontally using k8s in the VM, and if limits reached, scale vertically in
Hetzner?"*

Inputs: [hosting-feasibility.md](hosting-feasibility.md) (CX33 €8.49/mo,
~7 KB/event, tier margins), [dashboard-and-billing-research.md](dashboard-and-billing-research.md)
(Paddle webhook → cloud-init provisioning flow), and the current codebase
(no tenant concept anywhere: API keys, events, and subscriptions are
instance-global).

## The four candidate models

| | 1. VM per customer (compose) | 2. Bin-packed stacks on shared VMs | 3. One multi-tenant app+DB | 4. k8s pods per customer |
|---|---|---|---|---|
| Code changes | **none** | none | **large**: tenant_id on all 5 tables + every query, tenant-scoped keys, per-tenant rate limits/retention/backup/export | none (but new platform) |
| Data isolation | physical (sells itself: GDPR, enterprise optics) | physical per stack, shared host | row-level — one bug leaks a tenant | namespace-level |
| Blast radius | one customer | all customers on the host | **all customers** | node's customers |
| Noisy neighbor | impossible | disk/CPU contention | real; needs per-tenant throttling | mitigated by limits, still shared node |
| Infra cost @10 customers | ~€102/mo (10×CX33+backup) | ~€60/mo (2×CCX23) | ~€16/mo (1×CX43) | ~€75/mo + LB |
| Solo-operator ops | N stacks, uniform; scripted upgrades | + host packing/accounting | one stack, but every incident is everyone's incident | etcd, CNI, storage classes, upgrades — a platform to babysit |
| Marketing fit | **is the product promise** ("dedicated, single-tenant, EU") | weakens it | kills it | irrelevant to buyers |

**The margin math settles it**: infra is €10.19 of a £29 price (~35%). Model 3
would save ~€9/customer/month while destroying the single-tenant positioning,
adding the largest engineering project in the product's history, and making
backup/restore/data-exit per tenant *harder* (a shared TimescaleDB cannot
`pg_dump` one tenant cleanly). You would be spending the differentiation to
optimize a cost that is not the problem.

## The k8s verdict

**No — not at any stage a solo operator reaches.** "k8s inside the VM" is a
category error: one VM is one failure domain, so a cluster inside it adds
etcd, CNI and StatefulSet complexity without adding availability. Across VMs,
k3s would orchestrate ~1 pod per customer — which cloud-init + compose +
systemd already do with zero platform to maintain, and TimescaleDB is exactly
the stateful workload k8s is most painful for. Horizontal scaling in this
business is "another €8.49 VM for the next customer" (perfect linear sharding
by tenant, no code); vertical scaling is the documented CX33→CX43→CX53 ladder
when one customer outgrows their box. Revisit only past ~100 customers *and*
a second engineer, and even then bin-packing (model 2) comes first.

## Staged recommendation

**Stage 1 (customers 1–10): VM-per-customer, compose, semi-automated.**
Paddle webhook → control plane script → `hcloud` create (CX33, base snapshot,
firewall) → cloud-init writes `.env` + `docker compose up` → DNS
`{tenant}.eventcore.host` → health-poll → credentials portal email
(the flow in dashboard-and-billing-research.md). Product code changes: **zero**.
Platform artifacts to build: provisioning scripts + Paddle receiver (#35),
retention rotation (#31), off-VM backups (#33).

**Stage 2 (10–100): same model, fully automated fleet.** One
`fleet-upgrade` command (roll image tag, health-gate, one VM at a time),
central Prometheus scraping every instance's `/metrics` (the flow-stopped
alert doubles as customer monitoring), status page (#32), backup restore
drills. Optionally bin-pack *free trials* on one shared VM — trials never
had the dedicated promise.

**Stage 3 (100+): re-evaluate with data.** Margins hold linearly, so the
default is "keep going"; the pressure points will be ops toil (hire or
bin-pack with CCX dedicated hosts) — not the database, not k8s.

## What this decides for the boards

- #35 (provisioning) targets VM-per-customer with cloud-init — no k8s work.
- #31 (retention) runs per-instance — no tenant dimension needed.
- The dashboard (M11) authenticates per instance — tenancy comes free from
  the deployment model, exactly as the sold promise states.
- A multi-tenant "sandbox tier" is a possible future DIVERGE question, not a
  commitment.
