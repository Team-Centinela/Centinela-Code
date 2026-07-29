# Ingestion Service

Pulled out per ADR-001's **Selective Extraction Strategy** § (`../../docs/architecture/05-selective-extraction.md`).

## Why it is extracted

- **Independent scaling**: ingestion receives burst traffic; the monolith CPU profile is dominated by scoring + reporting reads. Different scales.
- **Failure isolation**: ingestion must remain reachable when Core Backend is down (outbox buffers events until broker recovers).
- **Independent deploy**: schema changes in `transactions` are decoupled from monolith deploys.

## What it owns

| Capability | Backing |
|---|---|
| HTTP API entry point for `POST /transactions` | Spring Boot @RestController |
| Persist transactions to PostgreSQL `oltp.transactions` | JPA / JOOQ adapter (`ports.outbound.oltp.*`) |
| Insert outbox event in same ACID tx | `outbox.outbox_events` |
| Run outbox publisher draining `transactions-raw` queue | Scheduled job `@1s` |
| Publish to Azure Service Bus queue `transactions-raw` | `spring-cloud-azure-starter-servicebus` binder adapter (per ADR-003 §3.1). Always-set `messageId` header = `outbox_events.id` (Phase 0.2.6 / §30.5 S2 #2 always-set contract; producer-side mirror of `TransactionsRawConsumer` H7 fix). |

## What it does **not** own

- Scoring (lives in the Serverless Engine, `services/serverless-engine/`)
- Case management (Core Backend, `services/core-backend/`)
- Rule configuration (admin module in Core Backend)

## Owned GitHub Issues

- **Implementation**: see `[#4 Sprint 1 — Foundation & Ingestion Pipeline (../.github/issues?filter=milestone)`
- Will appear under `sprint-1` label

## File layout

```
ingestion/
├── src/main/java/com/centinela/ingestion/
│   ├── api/                 # REST controllers (adapter inbound)
│   ├── domain/              # Pure domain types (TransactionInput, etc.)
│   ├── ports/inbound/       # Use cases
│   ├── ports/outbound/      # Repository / broker interfaces
│   ├── adapters/outbound/   # PostgreSQL / Service Bus implementations
│   └── Application.java     # Spring Boot main
├── pom.xml
└── Dockerfile
```

## Architecture Verification

Hexagonal-layer purity is enforced by `src/test/java/com/centinela/ingestion/archunit/HexagonalArchitectureTest.java` (4 ArchUnit rules):
1. Domain must not depend on Spring, JPA, or Azure packages
2. Domain classes must not carry Spring stereotype annotations
3. Domain must not access infrastructure adapters
4. Layered ordering: `domain` ← `application` ← `infrastructure`

Run: `mvn test` (executes as part of the full suite, no special profile needed).

References:
- [ADR-001 §Hexagonal](../../docs/decision-log/ADR-001-modular-monolith-hexagonal.md) — port/adapter layout mandatory for Ingestion API
- [Hexagonal Architecture](../../docs/architecture/03-hexagonal-architecture.md) — domain is the innermost ring
- `../../docs/architecture/01-overview.md` §"Ingestion"
- `../../docs/architecture/05-selective-extraction.md` §"Ingestion API"
- `../../docs/decision-log/ADR-002-postgresql-only-db.md` §"Storage matrix"
- `../../docs/decision-log/ADR-003-async-messaging-reliability.md` §"3.2 Outbox"
- **Dependency pinning**: per ADR-003 §3.1 (issue #26, #39), Ingestion API shares the Core Backend Spring Cloud Azure Service Bus binder chain (`spring-cloud-azure-starter-servicebus` 5.19.0 + `spring-cloud-stream` 4.3.3 + `azure-messaging-servicebus` 7.17.7). Versions declared in parent POM `services/pom.xml`.
