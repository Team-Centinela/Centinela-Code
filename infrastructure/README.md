# infrastructure/

Terraform IaC for the Centinela platform.

Per `architecture/06-technology-stack.md` the key resources are:

| Resource | Notes |
|---|---|
| Azure Resource Group | Bounded scope for budget caps |
| Azure Database for PostgreSQL Flexible Server | B1ms, nightly auto-stop, dev-only prod |
| Azure Service Bus (Standard tier) | Required: tier supports Topics |
| Azure Blob Storage | `documents-worm` container, LRS Hot, WORM policy (7y time-based) |
| Azure Container Apps | Hosts extracted services + modular monolith |
| Azure Static Web Apps | Frontend CDN |
| Azure Application Insights | Observability + cost telemetry |
| Azure Key Vault | Secrets only; keys managed by IaC |

See the soon-to-be-added ADRs for details:

- **`decision-log/ADR-002-postgresql-only-db.md`** — storage layout
- **`decision-log/ADR-003-async-messaging-reliability.md`** — Service Bus Standard tier rationale
- **`decision-log/ADR-006-security-auth`** (issue [#3](https://github.com/Team-Centinela/Centinela-Code/issues/3)) — auth & secrets
- **`decision-log/ADR-007-observability-iac-cost`** (issue — pending) — observability & cost caps

## Cost guardrails

- Estimated 21-day cost: **~$20–26** (well under $60 ceiling)
- Resource Group **budget alarm** at 50%, 80%, 100% of $60 → email the team
- PostgreSQL **auto-stop** schedule (auto-pause after 1h idle)
- Container Apps **scale to zero** on FA1 profile

## Day-1 region & quota verification

Every Azure service in this directory must be verified against the assigned region and the Azure Free Account tier limits **before** any IaC is applied. See [`REGION-QUOTA-CHECK.md`](REGION-QUOTA-CHECK.md) for the verification matrix and audit log (issue #34). Any service that resolves to `no` requires an ADR amendment issue opened before Week 1 starts.

## WORM ownership

Per ADR-002 §WORM policy, the `documents-worm` container's immutable blob policy is owned by the `centinela-platform@centinela.onmicrosoft.com` AAD group (Terraform variable `worm_owner_group_principal`). **Do not** shorten the immutability retention period once set — policies are themselves immutable. Lifecycle (Hot → Cool at 90d, Cool → Archive at 1y) is managed by `azurerm_storage_management_policy` and is **separate** from the immutability policy.

See:
- [ADR-002 storage matrix](docs/decision-log/ADR-002-postgresql-only-db.md#storage-matrix)
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
