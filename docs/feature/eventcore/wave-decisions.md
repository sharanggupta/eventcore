# DIVERGE Decisions — eventcore

**Date**: 2026-07-05 | **Agent**: Flux (nw-diverger) | **Work type**: brownfield (whole-product direction)

## Key Decisions

- [D1] Job extracted at strategic level with dual facets (forensic + propagation) rather than treating the feature-gap list as the problem: brainstorming targeted the job, not the absence list (see: diverge/job-analysis.md)
- [D2] Opportunity scores are desk-research proxies, explicitly flagged — no live customer interviews were possible this session; DISCOVER validation required before high-stakes reliance (see: diverge/job-analysis.md)
- [D3] Research at comprehensive depth found the white space: no product serves both facets self-hosted; EventCore's premise is unique but currently below propagation table stakes (see: diverge/competitive-research.md, patterns P1–P4)
- [D4] Developer-tool weight profile locked before scoring (DVF 25 / T1 15 / T2 20 / T3 15 / T4 25); no post-hoc adjustments (see: diverge/taste-evaluation.md)
- [D5] Recommendation follows the matrix with zero overrides: Pipeline Control Tower, 4.57, a 0.77 margin (see: recommendation.md)
- [D6] Single-Binary Edition removed during curation as an orthogonal packaging concern — recorded for possible reuse by DESIGN/DELIVER (see: diverge/options-raw.md §4)

## Job Summary

- Validated job (desk-validated; strategic level): *When my software system performs actions that customers, auditors, or dependent systems rely on, I want each action durably recorded and conveyed to every party that depends on it, so I can prove what happened and keep dependents consistent — without building event infrastructure myself.*
- ODI outcomes: 8 outcome statements (O1–O8); top opportunities O3 (15.0), O1 (14.0), O2 (13.0), O8 (13.0), O5 (12.5) — proxy scores

## Options Evaluated

- 11 raw options generated (7 SCAMPER + 4 Crazy 8s), curated to 6 structurally diverse options; all 6 survived the DVF filter (no option below the <6 threshold; nearest miss Source Capture at 7)
- Recommended: **Pipeline Control Tower** (4.57) — closes the propagation table-stakes gap (dead letters, redelivery, attempt logs, per-subscription filters, flow monitors, metrics) using data the system already produces
- Dissent: **Durable Pull Subscriptions & Replay** (3.80) — better if strategy favors differentiation via the durable log (the one asset webhook gateways cannot copy) over category parity; re-open if DISCUSS finds adopters choose EventCore for the permanent log

## SSOT Updates

- jobs.yaml: **created** (SSOT bootstrap) with job JOB-001 and outcomes O1–O8; changelog entry references feature eventcore
