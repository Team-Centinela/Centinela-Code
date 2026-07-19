# ADR-001: Modular Monolith with Hexagonal Architecture

## Status
**ACCEPTED** — Ratified during Sprint 0 ADR review (#16). See `architecture/03-hexagonal-architecture.md` for the canonical package layout.

## Context

Building a real-time fraud detection engine with budget constraints, a small team familiar with Spring Boot, and a 3-week delivery deadline. A full microservices mesh introduces operational overhead (service discovery, distributed tracing, deployment coordination) that the team cannot absorb. However, a traditional layered MVC architecture tightly couples business logic to frameworks, making the fraud detection rules difficult to test in isolation and impossible to extract later.

## Decision

Adopt **Modular Monolith + Hexagonal Architecture**:

1. **Deployment**: Single Spring Boot application (the monolith)
2. **Internal structure**: Each module follows Hexagonal Architecture with Ports & Adapters as specified in `architecture/03-hexagonal-architecture.md`
3. **Cross-module communication**:
   - **In-monolith** modules coordinate via Spring's `ApplicationEventPublisher` (in-process, transactional listener). The publisher writes to the outbox in the same ACID transaction.
   - **Cross-deployment** hops (Ingestion API, Serverless Engine, OCR Worker, future microservices) use Azure Service Bus. The Outbox publisher relays events from the outbox table to the broker.
   - Direct method calls between modules are **forbidden** — only ports/adapters at the boundary, never concrete packages.
4. **Extracted services**: Ingestion API, Serverless Engine (Rule Engine), and OCR Worker each run independently on Azure Container Apps (Consumption). The Core Backend hosts the remaining modules. The Serverless Engine is the third extracted service qualified under §"When a Module Qualifies for Extraction" with three of the listed criteria.

## Consequences

### Positive
- Business logic is pure Java, testable without Spring or infrastructure
- Framework replacements (DB, message broker, web framework) only affect adapter layer
- Any module can be extracted to a microservice via Strangler Fig pattern without rewriting
- Team can work on different modules concurrently with minimal merge conflicts

### Negative
- Requires discipline to maintain module boundaries and prevent dependency leakage
- Cross-module interactions are eventually consistent (no ACID guarantees across modules)
- Team needs to learn Hexagonal layering if coming from traditional MVC
- Three independent services (Ingestion API, Serverless Engine, OCR Worker) each carry their own CI/CD revision history and quota. Mitigated by shared IaC and a single `services/pom.xml` parent POM; the Serverless Engine shares the Ingestion API's Spring Cloud Azure Service Bus binder chain (`ADR-003` §3.1).

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Full Microservices | Too operationally heavy for 3 weeks and 4-person team |
| Traditional Layered MVC | Tight coupling, poor testability, hard to extract modules later |
| Azure Functions (event-triggered) for the Rule Engine | Cold starts in Functions Hosting Plans hit p99 latency above the real-time fraud detection budget; Functions Premium would erase the serverless-cost win. The Serverless Engine is implemented as a Spring Boot application on Azure Container Apps Consumption with a KEDA `azure-servicebus` scaler instead — pay-per-second, scale to zero, no cold-start cliff above ~1 s. |
| Event Sourcing + Full CQRS | Too complex for MVP; can be introduced incrementally if needed |

## References

- `architecture/03-hexagonal-architecture.md` — canonical package layout per module (ratified via #31)
- `architecture/04-event-driven-communication.md` — in-process + out-of-process event flow (ratified via #32)
- ADR-002: Unified PostgreSQL Persistence (`decision-log/ADR-002-postgresql-only-db.md`)
- ADR-003: Async Messaging & Reliability (`decision-log/ADR-003-async-messaging-reliability.md`)
- ADR-004: Rule Engine — Pipeline Pattern (`decision-log/ADR-004-rule-engine-pipeline-explainer.md`)
- ADR-005: Monorepo Unification (`decision-log/ADR-005-monorepo-unification.md`)
- `architecture/01-overview.md` — system-level narrative of this ADR
