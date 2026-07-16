# Core Backend (Modular Monolith)

Per ADR-001 (`docs/decision-log/ADR-001-modular-monolith-hexagonal.md`), this is the **primary deployment artifact**. It hosts all modules that have not been selectively extracted.

## Modules in this deployment

Per `docs/architecture/02-modular-monolith.md`:

| Module | Responsibility |
|---|---|
| `app.cases`     | Fraud case lifecycle and analyst workflow |
| `app.alerts`    | Alert rendering and notification fan-out |
| `app.scoring`   | Pipeline Pattern (FR-1..FR-4) → AggregatedFraudScore |
| `app.rules`     | Rule configuration loading + admin API |
| `app.reporting` | Read-replica queries; aggregate views |
| `app.auth`      | JWT issuance, role lookup |
| `app.admin`     | Cross-module admin endpoints, audit log access |
| `app.transactions` | Application layer for transaction query API (Ingestion owns writes) |

## Communication

Inter-module communication is **async** via Azure Service Bus (`decision-log/ADR-003-async-messaging-reliability.md` plus `architecture/04-event-driven-communication.md`).

Topics and queues owned by Core Backend:
- Topic: `case-events` (subscriptions: `reporting-updates`, `alerts`)
- Queue: `transactions-raw` (consumer)
- Queue: `documents-pending` (publisher)

## Testing per module

Per `best-practices/02-testing-strategy.md`:
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
│       ├── scoring/
│       ├── rules/
│       ├── reporting/
│       ├── auth/
│       ├── admin/
│       └── transactions/
└── pom.xml
```
