# Core Backend (Modular Monolith)

Per ADR-001 (`../../docs/decision-log/ADR-001-modular-monolith-hexagonal.md`), this is the **primary deployment artifact**. It hosts the modules that have not been selectively extracted. The Rule Engine lives in its own extracted service, the **Serverless Engine** (`../serverless-engine/`); see `../../docs/architecture/05-selective-extraction.md`.

## Modules in this deployment

Per `../../docs/architecture/02-modular-monolith.md`:

| Module | Responsibility |
|---|---|
| `app.cases`     | Fraud case lifecycle and analyst workflow |
| `app.alerts`    | Alert rendering and notification fan-out |
| `app.reporting` | Read-only queries via `reporting_reader` role |
| `app.auth`      | JWT issuance, role lookup |
| `app.admin`     | Cross-module admin endpoints, audit log access |

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
