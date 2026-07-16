# ADR-002: Unified PostgreSQL Persistence

## Status
**DRAFT** — Pending team review and approval (supersedes the historical polyglot proposal at [github.com/Team-Centinela/Centinela-docs/issues/3](https://github.com/Team-Centinela/Centinela-docs/issues/3), now closed).

## Context

`ASSIGNMENT.md` §E requires defending the storage engine choice based on the access patterns of each data type:

| Access pattern | Expected read pattern |
|---|---|
| Transactions & scores | High-volume writes, partition lookup by `accountId` |
| Fraud cases | Low volume, highly relational (Case ↔ Analyst ↔ Resolution ↔ Audit log) |
| Verification documents | Binary, write-once-read-rarely |

Two competing storage strategies have been proposed:

1. **Polyglot** (historical draft — [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3), now closed): Azure Cosmos DB for transactions, PostgreSQL for cases, Azure Blob Storage for documents.
2. **Unified PostgreSQL** (`architecture/06-technology-stack.md` + `architecture/02-modular-monolith.md`): one PostgreSQL Flexible Server with schema-per-module; one Blob Storage account for documents only.

The two proposals contradict each other and must be reconciled before Sprint 1 begins.

### Decision drivers

1. **Budget**: $60 USD hard limit over 21 days. Cosmos DB Serverless + PostgreSQL + Blob Storage are individually cheap, but operating two data engines for one data class is wasted spend.
2. **Operational simplicity** (`ASSIGNMENT.md` §T.1): 4-person team in 3 weeks, no SRE.
3. **Reporting** (`architecture/02-modular-monolith.md`): Reporting module uses a PostgreSQL read-replica. Joining across data classes (e.g. case → account → transactions) requires a single engine.
4. **Service Bus + Outbox** (`patterns/03-outbox-pattern.md`): the outbox table is already PostgreSQL. A polyglot strategy would force the previous-Service-Bus observers to either (a) read from Cosmos to build the outbox or (b) maintain two transactional origins per event.
5. **PostGIS extension** (`architecture/06-technology-stack.md`): geolocation checks (`FR-3 Impossible Location`) require spatial queries that PostgreSQL handles natively via PostGIS.

### Cost comparison (3-week Azure spend)

| Strategy | Component | SKU/Tier | Est. cost (21 days) |
|---|---|---|---|
| **Unified PostgreSQL** | Azure Database for PostgreSQL Flexible Server | B1ms (1 vCore / 2 GiB), with nightly auto-stop | **~$5–8** |
| | Azure Blob Storage | LRS Hot, near-zero | **<$1** |
| | (no Cosmos DB, no extra DB engines) | — | **$0** |
| | **Subtotal** | | **~$6–9** |
| **Polyglot** | Azure Cosmos DB | Serverless (Free tier: 1000 RU/s + 25 GB free) | **$0** |
| | Azure Database for PostgreSQL | B1ms, no auto-stop (always-on to host outbox) | **~$9–12** |
| | Azure Blob Storage | LRS Hot | **<$1** |
| | **Subtotal** | | **~$10–13** |

Both strategies fit the $60 budget. The cost gap is, on its own, **not** the deciding factor. The deciding factors are operational simplicity and join/reporting capability.

## Decision

**Adopt Unified PostgreSQL for all relational data, with Azure Blob Storage for binary large objects (BLOBs) only.**

### Storage matrix

| Data class | Engine | Schema / Container | Partition / access |
|---|---|---|---|
| Transactions | PostgreSQL `oltp` schema, table `transactions` | Partition by `accountId` (hash partitioning) | "Get recent transactions for account X" → partition scan |
| FraudCases, AuditLog, Users, RuleConfigs | PostgreSQL various schemas | Schema-per-module | Relational queries, FK joins |
| VerificationDocuments metadata | PostgreSQL `documents` schema | Schema-per-module | Relational queries, FK joins |
| Verification document blobs | Azure Blob Storage | Container `documents-worm` (WORM policy, LRS Hot) | "Write once, read rarely" |
| Outbox events | PostgreSQL `outbox` schema | — | Required by `patterns/03-outbox-pattern.md` |

**Cosmos DB is not used in any capacity.** The historical polyglot proposal at [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) is superseded.

## Consequences

### Positive
- One DB engine, one backup story, one IAM path, one connection pool story. Reduces ops surface area for a 4-person, 3-week team.
- Reporting read-replica joins across data classes natively; no ETL or materialized views required.
- PostGIS enables the `FR-3` geo-velocity check `WHERE ST_Distance(...)` queries at zero licensing cost.
- Outbox events live in the same engine as the business tables — atomic writes, no two-phase.
- PostgreSQL `JSONB` covers the few "schemaless" reasonability needs (e.g., `raw_evidence` for triggered rules).

### Negative
- Credits toward project budget ceiling cannot route through Cosmos DB free tier.
- Schema-per-module discipline is required to prevent accidental cross-schema joins; Public schema will be visible to all roles but used only by the central module in each context.
- Hash partitioning by `accountId` requires manual partition creation as account cardinality grows. For the 3-week window, a fixed 16-hash strategy is sufficient.

### Mitigation strategies for the negatives

| Negative | Mitigation |
|---|---|
| Cross-schema boundary leakage | Flyway migrations are namespaced per schema; CI rejects any migration referencing another module's schema. |
| Hash-partition hot spots | Use modulo 16 hashing by default; for V2, rebalance to range/hash composite if hotspot observed. |
| Outbox noise | Clean up `outbox_events WHERE status='SENT' AND sent_at < NOW() - INTERVAL '7 days'` weekly (`patterns/03-outbox-pattern.md`). |

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Full polyglot (Cosmos+Postgres+Blob). Historical [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3). | Two engines for two tables is over-engineered for 3 weeks. No unique capability (spatial, JSONB handled by PostgreSQL). Joins broken across engines. |
| All Azure Cosmos DB (no PostgreSQL). | No ACID transactions, relational joins for case metadata broken, no flyway-style migrations, no PostGIS. |
| All Azure SQL. | Lock-in to a SQL Server dialect, no PostGIS for KV calculations; PostgreSQL is preferred by the team. |
| SQLite local file per service. | No shared infra, breaks the modular-monolith discipline, complicates IaC. |

## References

- `ASSIGNMENT.md` §E — Persistence strategy requirement
- `architecture/02-modular-monolith.md` — Schema-per-module rule
- `architecture/06-technology-stack.md` — Technology stack baseline
- `patterns/03-outbox-pattern.md` — Outbox Pattern in PostgreSQL
- `architecture/04-event-driven-communication.md` — Domain Events
- Historical GitHub Issue [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) (polyglot draft) — superseded
- Historical GitHub Issue [Team-Centinela/Centinela-docs#7](https://github.com/Team-Centinela/Centinela-docs/issues/7) (Observability/Cost draft) — supersedes the cost rows for Cosmos DB
- [#4](https://github.com/Team-Centinela/Centinela-Code/issues/4) Sprint 1 — actions updated; no Cosmos module to provision in Week 1
- [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) ADR-002 issue tracker — Supersedes [Team-Centinela/Centinela-docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3)

## Status

**DRAFT** — pending Sprint 0 review on [`gh issue list --label adr --state open`](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aadr). This ADR clusters with [#1, #12, #13](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aadr); if #11 is approved, [#11](https://github.com/Team-Centinela/Centinela-Code/issues/11) must be moved out of *draft* label and the historical [docs#3](https://github.com/Team-Centinela/Centinela-docs/issues/3) stays closed.
