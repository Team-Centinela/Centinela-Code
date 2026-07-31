# Core Backend (Modular Monolith)

Per ADR-001 (`../../docs/decision-log/ADR-001-modular-monolith-hexagonal.md`), this is the **primary deployment artifact**. It hosts the modules that have not been selectively extracted. The Rule Engine lives in its own extracted service, the **Serverless Engine** (`../serverless-engine/`); see `../../docs/architecture/05-selective-extraction.md`.

## Modules in this deployment

Per `../../docs/architecture/02-modular-monolith.md`:

| Module | Responsibility | Status |
|---|---|---|
| `app.cases`     | Fraud case lifecycle and analyst workflow | **Active (Sprint 1, #273)** — `cases.cases` table, `CaseEntity` + Spring Data repository, `CreateCaseFromEvaluationUseCase`, `CaseEventsConsumer` subscribed to the `case-events` topic subscription `core-backend-sub`, `/actuator/health/cases` indicator. |
| `app.alerts`    | Alert rendering and notification fan-out | Stub |
| `app.reporting` | Read-only queries via `reporting_reader` role | Stub |
| `app.auth`      | JWT issuance, role lookup | Stub |
| `app.admin`     | Cross-module admin endpoints, audit log access | Stub |

> The `app.transactions` module is **not** in Core Backend per ADR-002 alignment. Transaction writes and the query API live in the **Ingestion API** (`../ingestion/`), which owns the `oltp` schema. See §ADR-002 Reconciliation below.

Scoring, the rule pipeline, and the explainer are **not** in this module set. They live in the **Serverless Engine** extracted service (`../serverless-engine/`). See `../../docs/architecture/05-selective-extraction.md` and `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md`.

## ADR-002 Reconciliation

Per **ADR-002 §Storage matrix** (`../../docs/decision-log/ADR-002-postgresql-only-db.md`), the sanctioned Core-Backend-owned schemas are:

| Schema | Owner |
|---|---|
| `cases` | Cases module |
| `alerts` | Alerts module |
| `reporting` | Reporting module |
| `auth` | Auth module |
| `rules_config` | Rules configuration module |

No `accounts` or `transactions` schemas are present in Core Backend — those belong in the `oltp` schema owned by the **Ingestion API** (`../../docs/architecture/01-overview.md` §System Components).

## Communication

Per ADR-001 §Decision:

- **In-monolith** module coordination uses Spring's `ApplicationEventPublisher` (in-process, transactional) — no Service Bus latency within the monolith.
- **Cross-deployment** hops (Ingestion API, Serverless Engine, OCR Worker) use Azure Service Bus via the Outbox pattern (`../../docs/patterns/03-outbox-pattern.md`).
- Direct method calls between modules are **forbidden** — only ports/adapters at the boundary.

See `../../docs/architecture/04-event-driven-communication.md` and `../../docs/decision-log/ADR-003-async-messaging-reliability.md`.

**Dependency pinning**: per ADR-003 §3.1 client library pinning (issue #26), the Core Backend uses `com.azure.spring:spring-cloud-azure-starter-servicebus`.

## Cases module (Lane D, #273)

The Cases module is the first fully wired in-monolith module per ADR-001 §Hexagonal Architecture. Package layout (`com.centinela.cases.*`):

| Layer | Files |
|---|---|
| `domain/model/` | `Case`, `CaseStatus` (immutable records, no Spring/JPA imports) |
| `domain/port/` | `CaseRepository` (outbound port, Java interface) |
| `application/` | `CreateCaseFromEvaluationUseCase` (ACID transaction boundary; gates on `IdempotencyService.tryProcess(...)` and on `recommendation == BLOCK` OR `score >= scoreThreshold`; default threshold `70` per ADR-004 §4.4) |
| `adapter/in/messaging/` | `CaseEventsConsumer` (functional `@Bean Consumer<Message<String>> caseEventsIn()`; `caseEventsIn-in-0` binding → `case-events` topic subscription `core-backend-sub`) |
| `adapter/in/observability/` | `CasesHealthIndicator` (`/actuator/health/cases`) |
| `adapter/out/persistence/` | `CaseEntity` (JPA, `cases.cases` schema), `SpringDataCaseRepository`, `JpaCaseRepository` (port adapter) |

The case-creation contract is `case_opens iff recommendation == BLOCK OR score >= 70` (ADR-004 §4.4). The `IdempotencyService.tryProcess("core-backend.case-events", transactionId)` gate precedes the decision and the row write; a duplicate aggregateId short-circuits to `Outcome.DUPLICATE` (ADR-003 §3.3.1 processed_events ledger).

Migrations:

- `db/migration/V4__cases_table.sql` (PostgreSQL) + `db/migration-h2/V4__cases_table.sql` (H2) — `cases.cases` table with JSONB evidence, indexes on `transaction_id` / `(status, opened_at)` / `(account_id, opened_at)`.
