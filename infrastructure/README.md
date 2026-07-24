# infrastructure/

Terraform IaC for the Centinela platform.

Per `../docs/architecture/06-technology-stack.md` the key resources are:

| Resource | Notes |
|---|---|
| Azure Resource Group | Bounded scope for budget caps |
| Azure Database for PostgreSQL Flexible Server | B1ms, nightly auto-stop, dev-only prod |
| Azure Service Bus (Standard tier) | Required: tier supports Topics |
| Azure Blob Storage | `documents-worm` container, LRS Hot, WORM policy (7y time-based) |
| Azure Container Apps (Consumption) | All four backends with KEDA HTTP/Service Bus scalers; per-second billing; scale to zero. Full deployment table and scaler breakdown: ADR-009 §9.1. Serverless Engine Maven module: [#51](https://github.com/Team-Centinela/Centinela-Code/issues/51). |
| Azure Static Web Apps | Frontend CDN |
| Azure Application Insights | Observability + cost telemetry |
| Azure Key Vault | Secrets only; keys managed by IaC |

See the ADRs for each technology's rationale:

| ADR | What it covers |
|-----|---------------|
| ADR-002 | Storage layout |
| ADR-003 | Service Bus Standard tier + reliability |
| ADR-009 | Compute substrate (ACA Consumption + SWA Free) |
| ADR-006 | Auth & secrets |
| ADR-007 | Observability & cost caps |
| ADR-010 | Issue & PR discipline (companion infra issues, blockers, cost attribution) |

## Cost guardrails

| Resource | Plan | Est. 21-day cost | Notes |
|---|---|---|---|
| Azure Container Apps (Consumption) | Pay-per-second, free grant first 180k vCPU-seconds + 360k GiB-seconds + 2M requests/month/subscription | **~$0–5** | Four backends share one ACA Environment; scale to zero when idle. Free grant covers expected Sprint 1–3 traffic. |
| Azure Static Web Apps | Free | **$0** | 100 GB bandwidth/month included; enough for SPA demo traffic. |
| Azure Database for PostgreSQL Flexible Server | B1ms (1 vCore / 2 GiB), nightly auto-stop | **~$5–8** | Auto-stop after 1h idle saves ~40% vs always-on. |
| Azure Service Bus (Standard tier) | ~$10/month base; first 13M ops/month free | **~$7** | 21-day pro-rata. Required for Topics (case-events). |
| Azure Blob Storage | LRS Hot, near-zero for 21 days | **<$1** | Documents metadata in PostgreSQL; only document blobs here. |
| Azure Cognitive Services — Document Intelligence | Free (F0) | **$0** | 500 pages/month free quota. |
| Azure Key Vault | Standard | **<$1** | 25,000 transactions/month free. |
| Azure Application Insights | Pay-per-gig, first 5 GB/month free | **~$2** | |
| **Total** | | **~$15–24** | Well under the $60 ceiling. |

Key cost-saving mechanisms (scale-to-zero, free grants, PostgreSQL auto-stop, budget alerts at 50/80/90/100% of $60) are canonical in [`../docs/decision-log/ADR-007-observability-cost-telemetry.md`](../docs/decision-log/ADR-007-observability-cost-telemetry.md) §7.7 and [`../docs/decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md`](../docs/decision-log/ADR-009-compute-substrate-container-apps-static-web-apps.md) §Cost Impact.

## Per-issue cost attribution (per ADR-010 §10.5)

Every companion infra issue records its spend in one of two modes:

- **Mode (a)** — add a row to this document's §"Cost guardrails" table (one row per Azure-impact change), referencing the `centinela:lp` and `centinela:issue` tags.
- **Mode (b)** — tag the affected Azure resource(s) with the schema below. Back-trace from Azure Cost Analysis (filter by `Resource Tags.centinela:lp`, group by `Resource Tags.centinela:issue`).

```
centinela:lp        = "<lane letter>"        # "lane-b", "lane-c", etc.
centinela:epic      = "[<lane>.0]"           # the per-lane epic issue ref
centinela:issue     = "<companion issue>"    # the companion issue, "[B.A1]"
centinela:sprint    = "sprint-<N>"
centinela:start     = "<yyyy-mm-dd>"
centinela:close     = "<yyyy-mm-dd, filled at close>"
centinela:action    = "create|update|delete"
```

For Tier-1 resources (RG, Service Bus Standard namespace, ACA Environment, Key Vault, App Insights workspace) the **gold standard is (a)+(b)**. For lower-tier changes either is the floor.

Per-lane cost rows (live):

| lane | service | sprint | resource delta (or note) | cost | companion | start | spent |
|---|---|---|---|---|---|---|---|
| lane-a | governance | _process_ | PR #120/#121/#127 acknowledge path | _doc only_ | _regex 131_ | 2026-07-25 | — |
| _pending_ | _pending_ | sprint-1 | _companion issues [B.A1] etc. land rows here as they close_ | _TBD_ | _TBD_ | _TBD_ | _TBD_ |

## Day-1 region & quota verification

Every Azure service in this directory must be verified against the assigned region and the Azure Free Account tier limits **before** any IaC is applied. See [`REGION-QUOTA-CHECK.md`](REGION-QUOTA-CHECK.md) for the verification matrix and audit log (issue #34). Any service that resolves to `no` requires an ADR amendment issue opened before Week 1 starts.

## WORM ownership

Per ADR-002 §WORM policy, the `documents-worm` container's immutable blob policy is owned by the `centinela-platform@centinela.onmicrosoft.com` AAD group (Terraform variable `worm_owner_group_principal`). **Do not** shorten the immutability retention period once set — policies are themselves immutable. Lifecycle (Hot → Cool at 90d, Cool → Archive at 1y) is managed by `azurerm_storage_management_policy` and is **separate** from the immutability policy.

See:
- [ADR-002 storage matrix](../docs/decision-log/ADR-002-postgresql-only-db.md#storage-matrix)
- [`REGION-QUOTA-CHECK.md`](REGION-QUOTA-CHECK.md) — Day-1 region/quota verification (issue #34)
- Issues: [Day-1 quotas #15](https://github.com/Team-Centinela/Centinela-Code/issues/15), [Sprint 0 repo cleanup #19](https://github.com/Team-Centinela/Centinela-Code/issues/19)

## Service Bus ownership

Per ADR-003 §3.4 (issue #27), `max_delivery_count = 3` and `<entity>-poison` siblings are IaC-owned and **must not** be overridden in application config:

- Module root: `infrastructure/modules/servicebus/main.tf`
- Per-entity files planned at `infrastructure/services/queues-transactions-raw.tf`, `queues-documents-pending.tf`, `topics-case-events.tf`
- All three queues (`transactions-raw`, `documents-pending`) and both topic subscriptions (`case-events/reporting-updates`, `case-events/alerts`) carry `max_delivery_count = 3`.
- Poison routing: a sibling `<entity>-poison` queue is provisioned per queue, and `azurerm_servicebus_subscription_rule` forward-on-dead-letter is provisioned per subscription.
- Drift detection via `terraform plan` in CI; a `tflint`/`checkov` policy encodes "no queue/subscription ships without a `-poison` sibling".

Cost: 5 additional Standard-tier entities (~0 marginal; well under the $10/mo base charge).
