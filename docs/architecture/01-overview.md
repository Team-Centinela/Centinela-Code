# Architecture Overview

Centinela uses a **Modular Monolith with Hexagonal Architecture** — a single Spring Boot deployment where each module is internally organized in hexagonal layers and communicates via domain events over Azure Service Bus.

## What This Architecture Achieves

- **Domain isolation** — fraud detection logic lives in pure Java, decoupled from frameworks and infrastructure
- **Independent modules** — Transaction, Rule Engine, Case, Alert, Reporting, and Auth each own their data and logic with no direct cross-module calls
- **Future-proof extraction** — any module can be extracted to an independent microservice without rewriting business logic
- **Operational simplicity** — single deployment, single CI/CD, no service mesh overhead

## System Components

```
                    ┌──────────────────────────────────────────────┐
                    │              Modular Monolith                 │
                    │  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
                    │  │Transaction│  │   Rule   │  │   Case   │   │
                    │  │  Module   │──│  Engine  │──│  Module  │   │
                    │  └─────┬────┘  └────┬─────┘  └────┬─────┘   │
                    │        │            │              │         │
                    │  ┌─────┴────┐  ┌────┴─────┐  ┌────┴─────┐   │
                    │  │  Alert   │  │Reporting │  │   Auth   │   │
                    │  │  Module  │  │  Module  │  │  Module  │   │
                    │  └──────────┘  └──────────┘  └──────────┘   │
                    └─────────────────────┬────────────────────────┘
                                          │
                                ┌─────────┴─────────┐
                                │ Azure Service Bus  │
                                └─────────┬─────────┘
                                          │
                          ┌───────────────┴───────────────┐
                          │      Extracted Services        │
                          │  ┌──────────┐  ┌──────────┐    │
                          │  │Ingestion │  │   OCR    │    │
                          │  │   API    │  │  Worker  │    │
                          │  └──────────┘  └──────────┘    │
                          └───────────────────────────────┘
```

## Principles That Guide Decisions

| Principle | What It Means |
|-----------|---------------|
| **Domain-Centric** | Business logic is framework-agnostic; infrastructure is an adapter |
| **Module Isolation** | Each module owns its schema and logic; no shared databases or direct calls |
| **Event-Driven** | Modules coordinate asynchronously via Azure Service Bus domain events |
| **Selective Extraction** | Only extract to independent services when scaling, technology, or isolation demands it |
| **Testability** | Pure domain logic can be unit-tested without Spring, databases, or network |

## Related Documents

- [ADR-001: Modular Monolith + Hexagonal Architecture](../decision-log/ADR-001-modular-monolith-hexagonal.md)
- [ADR-002: Unified PostgreSQL Persistence](../decision-log/ADR-002-postgresql-only-db.md)
- [ADR-003: Async Messaging & Reliability](../decision-log/ADR-003-async-messaging-reliability.md)
- [ADR-004: Rule Engine — Pipeline Pattern](../decision-log/ADR-004-rule-engine-pipeline-explainer.md)
- [ADR-005: Monorepo Unification](../decision-log/ADR-005-monorepo-unification.md)
- [Technology Stack](06-technology-stack.md)
- [Hexagonal Architecture](03-hexagonal-architecture.md)
