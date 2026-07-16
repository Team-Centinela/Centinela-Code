# infrastructure/

Terraform IaC for the Centinela platform.

Per `architecture/06-technology-stack.md` the key resources are:

| Resource | Notes |
|---|---|
| Azure Resource Group | Bounded scope for budget caps |
| Azure Database for PostgreSQL Flexible Server | B1ms, nightly auto-stop, dev-only prod |
| Azure Service Bus (Standard tier) | Required: tier supports Topics |
| Azure Blob Storage | `documents-worm` container, LRS Hot, WORM policy |
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

See:
- [ADR-002 storage matrix](docs/decision-log/ADR-002-postgresql-only-db.md#storage-matrix)
- Issues: [Day-1 quotas #15](https://github.com/Team-Centinela/Centinela-Code/issues/15), [Sprint 0 repo cleanup #19](https://github.com/Team-Centinela/Centinela-Code/issues/19)
