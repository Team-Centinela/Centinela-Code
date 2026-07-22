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
| `app.transactions` | Application layer for transaction query API (Ingestion API owns writes; Serverless Engine owns scoring writes) |

> Scoring, the rule pipeline, and the explainer are **not** in this module set. They live in the **Serverless Engine** extracted service (`../serverless-engine/`). See `../../docs/architecture/05-selective-extraction.md` and `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md`.
>
> **ADR-002 Reconciliation — `accounts` schema**: The `accounts` schema and `account/` module were introduced by PR #43 before ADR-002 was finalized. Per ADR-002 §Storage matrix, `accounts` is **not** a sanctioned schema — account data belongs in the `oltp` schema owned by the Ingestion API (#53). Decision #61(b) confirmed the `account/` module as non-franchise; the schema and module have been deleted from this deployment. Outbox-only audit per ADR-003 is the canonical audit path.

## Communication

Per ADR-001 §Decision:

- **In-monolith** module coordination uses Spring's `ApplicationEventPublisher` (in-process, transactional) — no Service Bus latency within the monolith.
- **Cross-deployment** hops (Ingestion API, Serverless Engine, OCR Worker) use Azure Service Bus via the Outbox pattern (`../../docs/patterns/03-outbox-pattern.md`).
- Direct method calls between modules are **forbidden** — only ports/adapters at the boundary.

See `../../docs/architecture/04-event-driven-communication.md` and `../../docs/decision-log/ADR-003-async-messaging-reliability.md`.

**Dependency pinning**: per ADR-003 §3.1 client library pinning (issue #26), the Core Backend uses `com.azure.spring:spring-cloud-azure-starter-servicebus` (Spring Cloud Azure Service Bus binder) over `spring-cloud-stream` 4.x. Versions are pinned in the parent POM `services/pom.xml` (`<spring-cloud-azure.version>5.19.0</...>`, `<azure-messaging-servicebus.version>7.17.7</...>`, `<spring-cloud-stream.version>4.3.3</...>` — see PR #36 and #39).

Topics and queues owned by Core Backend:
- Topic: `case-events` (subscriptions: `reporting-updates`, `alerts`)
- Queue: `documents-pending` (publisher)

Consumer:
- Subscription: `case-events/reporting-updates`, `case-events/alerts`

## Testing per module

Per `../../docs/best-practices/02-testing-strategy.md`:
- Domain code: 100% line coverage requirement
- Use cases: 95% line coverage
- Adapters: smoke + integration tests against the real engine (PostgreSQL Testcontainers dev profile when applicable)

## Owned GitHub Issues

- **Implementation**: Sprint 1 ([#4](https://github.com/Team-Centinela/Centinela-Code/issues/4)) through Sprint 3 ([#6](https://github.com/Team-Centinela/Centinela-Code/issues/6))
- Core Backend has *no* dedicated quick-start issue; its work is part of sprints 1–3.

## File layout

TBD at Sprint 1 (`gh issue list --label backend`). Likely structure:

```
core-backend/
├── src/main/java/com/centinela/core/
│   ├── Application.java
│   ├── platform/   # Spring Boot base configuration, security wiring
│   └── modules/
│       ├── cases/
│       ├── alerts/
│       ├── reporting/
│       ├── auth/
│       ├── admin/
│       └── transactions/
└── pom.xml
```
