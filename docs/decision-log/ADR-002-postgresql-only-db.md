# ADR-002: Unified PostgreSQL Persistence

## Context

`ASSIGNMENT.md` §E requires defending the storage engine choice based on the access patterns of each data type:

| Data class | Access pattern |
|---|---|
| Transactions & scores | High-volume writes, partition lookup by `accountId` |
| Fraud cases | Low volume, highly relational (Case ↔ Analyst ↔ Resolution ↔ Audit log) |
| Verification documents | Binary, write-once-read-rarely |

### Decision drivers

1. **Budget** (`ASSIGNMENT.md` §3): $60 USD hard limit over 21 days (Due July 31, 2026).
2. **Operational simplicity** (`ASSIGNMENT.md` §T.1): 5-person team in 3 weeks, no SRE.
3. **Reporting** (`../architecture/02-modular-monolith.md`): joining across data classes (case → account → transactions) requires a single engine. A read-replica was dropped from scope — see issue #24 and §Read-replica removal rationale.
4. **Outbox** (`../patterns/03-outbox-pattern.md`): the outbox table is PostgreSQL; the unified strategy keeps the business write + outbox insert in the same ACID transaction.
5. **PostGIS** (`../architecture/06-technology-stack.md`): FR-3 Impossible Location needs spatial queries PostgreSQL handles natively.

### Cost envelope (storage engine, 21 days)

| Component | SKU | Est. cost |
|---|---|---|
| Azure Database for PostgreSQL Flexible Server | B1ms (1 vCore / 2 GiB), 12 h/day auto-stop on non-testing hours (see §Planned DB downtime & outbox restart-drain) | ~$5–8 |
| Azure Blob Storage (verification document blobs only) | LRS Hot, WORM | <$1 |
| **Storage-engine subtotal** | | **~$6–9** |

Full system budget (compute, messaging, observability, secrets, frontend, automation) is in `infrastructure/README.md` §Cost Guardrails (~$15–24 total). The polyglot alternative (Cosmos + PostgreSQL + Blob) sits in a comparable cost band; the deciding factors are operational simplicity and cross-class join capability, not dollars. See Alternatives considered for the rejected option.

## Decision

**Adopt Unified PostgreSQL for all relational data, with Azure Blob Storage for binary large objects (BLOBs) only.**

### Storage matrix

| Data class | Engine | Schema / Container | Partition / access |
|---|---|---|---|
| Transactions | PostgreSQL `oltp` schema, table `transactions` | Partition by `accountId` (hash partitioning) | "Get recent transactions for account X" → partition scan |
| FraudCases, AuditLog, Users, RuleConfigs | PostgreSQL various schemas | Schema-per-module | Relational queries, FK joins |
| VerificationDocuments metadata | PostgreSQL `documents` schema | Schema-per-module | Relational queries, FK joins |
| Verification document blobs | Azure Blob Storage | Container `documents-worm` — **7-year time-based immutable policy** (legal-hold disabled), LRS Hot; lifecycle: Hot → Cool after 90 days, Cool → Archive after 1 year. Owner: `centinela-platform@…` AAD group. See §WORM policy. | "Write once, read rarely" |
| Outbox events | PostgreSQL `outbox` schema | — | Required by `../patterns/03-outbox-pattern.md` |

**Cosmos DB is not used in any capacity.** The historical polyglot proposal at [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) is superseded.

### WORM policy (verification document blobs)

Per issue #33, the `documents-worm` container carries an Azure Blob Storage **immutable blob policy** with these fixed parameters. Immutable blob policies are themselves immutable once set, so the choice must be made before the bucket is provisioned.

| Attribute | Value | Rationale |
|---|---|---|
| Policy kind | `immutabilityPolicy` (time-based) | Audit-by-design (§E) + ASSIGNMENT §Audit-by-design; no per-blob legal hold required for the 21-day window. |
| Retention period | **7 years** (2557 days) | Default for financial fraud evidence; survives pilot → production. |
| Legal hold | **disabled** | No active litigation signal exists for pilot scope; revisitable per-archive if invoked. |
| Policy name | `centinela-worm-2026` | Stable, versioned with the year the policy is set. |
| Container name | `documents-worm` | Matches existing Terraform module; do not rename post-create. |
| Storage tier | LRS Hot | "Write once, read rarely" but reads are bursty during case escalation. |
| Write path | Service Bus-triggered only (OCR Worker, FastAPI) | Application identity scoped to `Blob Data Contributor` on the container. |
| Read path | Auditors + analysts via short-lived SAS tokens issued through Azure AD | Application identity does **not** have `Blob Data Reader` on the container directly. |
| Owner (RP) | `centinela-platform@centinela.onmicrosoft.com` AAD group | Documented in `infrastructure/README.md` §WORM ownership. |
| Lifecycle rule | Hot → Cool after 90 days; Cool → Archive after 1 year | Lifecycle is **separate** from the immutability policy; do not confuse the two. |
| IaC | `infrastructure/…/storage.tf` (module `documents_worm`) | Terraform `azurerm_storage_container` + `azurerm_storage_management_policy` + `azurerm_storage_container_immutability_policy`. |

> **Reversibility warning:** unchanged from #33 — once the immutability policy is set, it cannot be shortened. Plan accordingly.

### Read-replica removal rationale

Per issue #24, the originally-cited PostgreSQL read-replica for the Reporting module is **removed** from scope for the 21-day window. The B1ms Flexible Server SKU on the Azure Free tier does not support replicas in many regions, and provisioning one would push the project outside the $60/21-day budget.

Reporting connects to the **same primary** as every other module via a dedicated **read-only DB role** (`reporting_reader`) whose grants are limited to the `reporting` schema and a `SELECT`-only view surface across `oltp`, `cases`, `alerts`. The role is provisioned by Terraform and has no DDL/DML grants. This satisfies the ADR-002 §Decision driver 3 ("Joining across data classes requires a single engine") without operating a second database engine.

The read-replica remains an **ADR-amendable** option for V2 (Sprint 4+) once traffic outgrows the primary.

### Planned DB downtime & outbox restart-drain

`ASSIGNMENT.md` §3 (Constraints) explicitly states *"Resource cleanup/shutdown is required when not actively testing (to avoid burning budget over weekends)"*. To act on that constraint at minimal cost we use the B1ms **PostgreSQL Flexible Server Stop/Start capability**: a scheduled Azure Automation runbook (or `az postgres flexible-server stop`) puts the database into a stopped state during non-testing hours (weekday nights + weekends, ~12 h/day). The cost-comparison row above reflects this.

This has a direct interaction with the Outbox Pattern mandated by `ADR-003` – `../patterns/03-outbox-pattern.md`. The two designs are **compatible**, because:

1. While PostgreSQL is stopped, **no service can write to it** — the Ingestion API (which writes to the `oltp` schema and the `outbox` schema in the same ACID transaction) returns `503 Service Unavailable`. No new `outbox_events` rows can be inserted during this window, so no events are "in flight" without an active writer.
2. `outbox_events` rows inserted in the last write transaction **before** stop remain in `status='PENDING'` on the stopped database's storage. The Outbox Publisher does not need the database to remain *running* for events to be safe — it only needs the database to be *available* when it is time to drain.
3. When the Elastic Job / Azure Automation runbook issues `start`, PostgreSQL comes back online typically within 60–120 s. On its first poll after restart, each Outbox Publisher (Ingestion API, Serverless Engine worker, Core Backend) runs the existing `SELECT … ORDER BY created_at` query and drains the entire backlog. The `@Scheduled(fixedDelay = 1000)` cadence converges the backlog to `SENT` in seconds, not hours.
4. Service Bus receives the burst of events that accumulated during downtime shortly after restart; downstream consumers (Serverless Engine, OCR Worker) process them with no special handling beyond the consumer-side idempotency already mandated by `../patterns/06-idempotency-key.md`.

**Out of scope during the DB-down window:** clients that POST to the Ingestion API receive `503` with a `Retry-After` header. This is consistent with the "Real-Time" requirement in `ASSIGNMENT.md` §1.2 because tests are not executed during scheduled downtime; when the team is actively testing, PostgreSQL is running (see #10 — the auto-stop schedule excludes business-hours test blocks).

**What auto-stop does *not* solve and which ADR-003 gaps this keeps open:** auto-stop does not help against (a) broker-side outages — for which the Outbox Pattern was originally designed, (b) consumer-side poison messages, or (c) intermediate service crashes. Those are unaffected by DB availability and continue to be handled by the rest of `ADR-003`.

## Consequences

### Positive
- One DB engine, one backup story, one IAM path, one connection pool story. Reduces ops surface area for a 5-person, 3-week team.
- Reporting reads from the primary via a `SELECT`-only role; no read-replica to operate, no second connection pool to tune. Joins across data classes are native.
- PostGIS enables the `FR-3` geo-velocity check `WHERE ST_Distance(...)` queries at zero licensing cost.
- Outbox events live in the same engine as the business tables — atomic writes, no two-phase.
- PostgreSQL `JSONB` covers the few "schemaless" reasonability needs (e.g., `raw_evidence` for triggered rules).
- WORM 7-year policy + lifecycle (90d Hot→Cool, 1yr→Archive) preserves audit-evidence integrity while controlling storage spend across the 21-day window.

### Negative
- Credits toward project budget ceiling cannot route through Cosmos DB free tier.
- Schema-per-module discipline is required to prevent accidental cross-schema joins; Public schema will be visible to all roles but used only by the central module in each context.
- Hash partitioning by `accountId` requires manual partition creation as account cardinality grows. For the 3-week window, a fixed 16-hash strategy is sufficient.

### Mitigation strategies for the negatives

| Negative | Mitigation |
|---|---|
| Cross-schema boundary leakage | Flyway migrations are namespaced per schema; CI rejects any migration referencing another module's schema. |
| Reporting accidental writes against the primary | Provision a `reporting_reader` PostgreSQL role with `SELECT`-only grants; Reporting service connection string uses this role. CI denies DDL/DML through that role. |
| Hash-partition hot spots | Use modulo 16 hashing by default; for V2, rebalance to range/hash composite if hotspot observed. |
| Outbox noise | Clean up `outbox_events WHERE status='PUBLISHED' AND published_at < NOW() - INTERVAL '7 days'` weekly (`../patterns/03-outbox-pattern.md`). |
| Blob lifecycle confusion with immutability | Document in IaC comments; lifecycle rule sets `tier_to_cool` after 90d and `tier_to_archive` after 365d; immutability policy remains fixed at 7y. |

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Full polyglot (Cosmos+Postgres+Blob). Historical [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3). | Two engines for two tables is over-engineered for 3 weeks. No unique capability (spatial, JSONB handled by PostgreSQL). Joins broken across engines. |
| All Azure Cosmos DB (no PostgreSQL). | No ACID transactions, relational joins for case metadata broken, no flyway-style migrations, no PostGIS. |
| All Azure SQL. | Lock-in to a SQL Server dialect, no PostGIS for KV calculations; PostgreSQL is preferred by the team. |
| SQLite local file per service. | No shared infra, breaks the modular-monolith discipline, complicates IaC. |

## References

- `ASSIGNMENT.md` §E — Persistence strategy requirement
- `../architecture/02-modular-monolith.md` — Schema-per-module rule
- `../architecture/06-technology-stack.md` — Technology stack baseline
- `../patterns/03-outbox-pattern.md` — Outbox Pattern in PostgreSQL
- `../architecture/04-event-driven-communication.md` — Domain Events
- Historical GitHub Issue [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) (polyglot draft) — superseded
- Historical GitHub Issue [Team-Centinela/Centinela-docs#7](https://github.com/Team-Centinela/Centinela-docs/issues/7) (Observability/Cost draft) — supersedes the cost rows for Cosmos DB
- [#4](https://github.com/Team-Centinela/Centinela-Code/issues/4) Sprint 1 — actions updated; no Cosmos module to provision in Week 1
- [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) ADR-002 issue tracker — Supersedes [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3)
- [#24](https://github.com/Team-Centinela/Centinela-Code/issues/24) — Reporting read-replica removed (this ADR §Read-replica removal rationale)
- [#33](https://github.com/Team-Centinela/Centinela-Code/issues/33) — WORM policy pinned (this ADR §WORM policy)
- [#34](https://github.com/Team-Centinela/Centinela-Code/issues/34) — Day-1 region/quota verification (`infrastructure/REGION-QUOTA-CHECK.md`)
- [#10](https://github.com/Team-Centinela/Centinela-Code/issues/10) — Azure Budget Controls & PostgreSQL auto-stop schedule (delivers this ADR §Planned DB downtime & outbox restart-drain)

## Status

**ACCEPTED** (Sprint 0, 2026-07-17) — addresses blockers #24, #33, #34 raised in [#16](https://github.com/Team-Centinela/Centinela-Code/issues/16) Sprint 0 review. This ADR clusters with [#1, #12, #13](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aadr); the historical [docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) stays closed. The `draft` label on [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) is removed in the same release.
