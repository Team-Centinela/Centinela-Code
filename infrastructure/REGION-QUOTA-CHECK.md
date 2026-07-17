# Day-1 Region & Quota Verification

Per `ASSIGNMENT.md` §3, every Azure service the project depends on must be verified on **Day 1** against the Azure Free Account tier limits of the **assigned region**. Several ADR choices hinge on this verification (ADR-002 read-replica support, ADR-003 Service Bus Standard, ASSIGNMENT.md Document Intelligence F0 quota). If any row resolves to **no**, open an ADR amendment issue **before Week 1 starts**.

## How to use this file

1. Set **Region chosen** column to the team's region pick before the table is read.
2. Walk each row top-to-bottom and complete the verification on the Free account subscription.
3. Replace `yes / no / n/a` with the verified answer and a one-line Note (Azure CLI command + result excerpt is ideal).
4. If any **Available on Free?** is `no` → file an ADR amendment issue with the issue template `.github/ISSUE_TEMPLATE/adr.md`.
5. Update the ADR acceptance-criteria sections to reference the verified row (link cells to the verified report).

## Verification matrix

| Service | Region chosen | SKU / Tier chosen | Available on Free? | PostGIS supported? | Replicas supported? | Notes |
|---|---|---|---|---|---|---|
| Azure PostgreSQL Flexible Server | `<region>` | B1ms (1 vCore / 2 GiB) + nightly auto-stop | yes/no | yes/no | yes/no | `az postgres flexible-server show --resource-group ... --name ...` confirm tier & auto-stop; `SELECT extname FROM pg_extension WHERE extname='postgis';` confirm extension; `az postgres flexible-server replica list` confirm replica support (Reporting ADR-002 removed this dependency, but flag for V2). |
| Azure Service Bus Namespace | `<region>` | **Standard** (Topics) | yes/no | n/a | n/a | Service Bus Standard tier is **not** available on every Free subscription/region. `az servicebus namespace show --sku-name Standard` must succeed; Basic tier does **not** support Topics (`case-events`) required by ADR-003 — verify before locking the namespace. |
| Azure Service Bus Topic `case-events` | `<region>` | — | yes/no | n/a | n/a | Sub-resource of the namespace above; confirm topic + subscription create succeeds with the chosen tier. |
| Azure Service Bus Topic `transactions-raw` (queue) | `<region>` | — | yes/no | n/a | n/a | Queue create with Standard tier; max-delivery-count = 3 set via IaC (see ADR-003 §3.2 + #27). |
| Azure AI Document Intelligence | `<region>` | Free (F0) | yes/no | n/a | n/a | Monthly quota: 500 pages free / month (per region, subject to change). `az cognitiveservices account show --kind FormRecognizer --sku F0` + `_usage` endpoint. |
| Azure Application Insights | `<region>` | Free (per-node) | yes/no | n/a | n/a | First 5 GB / month free; 90-day retention at Free tier. |
| Azure Blob Storage (General-purpose v2) | `<region>` | LRS Hot | yes/no | n/a | n/a | WORM immutability policies supported on GPv2 in most regions; verify with `az storage account immutability-policy` create against a test container. |
| Azure Container Apps | `<region>` | Consumption (serverless) | yes/no | n/a | n/a | First 180,000 vCPU-seconds / month free; 2M requests / month free. |
| Azure Static Web Apps | `<region>` | Free | yes/no | n/a | n/a | 100 GB bandwidth / month free; sufficient for SPA frontend. |
| Azure Key Vault | `<region>` | Standard | yes/no | n/a | n/a | 25,000 transactions / month free; soft-delete on (default in IaC). |

## Verified status (fill when complete)

| Category | Status | Verified-by | Date |
|---|---|---|---|
| Region chosen | `<pending>` | `<name>` | `<YYYY-MM-DD>` |
| All rows resolved `yes` / `n/a` | `<pending>` | `<name>` | `<YYYY-MM-DD>` |
| ADR amendment issues opened (any `no`) | `<pending>` | `<name>` | `<YYYY-MM-DD>` |

## Audit log

| Date | Verified-by | Result | Notes |
|---|---|---|---|
| | | | |

## Acceptance criteria (mirroring #34)

- [ ] Region chosen column filled.
- [ ] Each ADR that depends on a quota has at least one acceptance row marked **verified Day 1**.
- [ ] Any region-blocked service has a corresponding ADR amendment issue opened **before** Week 1 starts.
- [ ] Audit log row added by the verifier.

## Related

- `ASSIGNMENT.md` §3 — Region Quotas
- [ADR-002 Unified PostgreSQL Persistence](../docs/decision-log/ADR-002-postgresql-only-db.md) — depends on B1ms + auto-stop + PostGIS support
- [ADR-003 Async Messaging & Reliability](../docs/decision-log/ADR-003-async-messaging-reliability.md) — depends on Service Bus **Standard** tier
- [`#16`](https://github.com/Team-Centinela/Centinela-Code/issues/16) Sprint 0 ADR review
- [`#34`](https://github.com/Team-Centinela/Centinela-Code/issues/34) — this issue
