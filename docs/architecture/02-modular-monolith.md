# Modular Monolith

A single deployable Spring Boot application whose internal code is organized into strict, bounded modules — each with its own domain, data, and responsibilities.

## What This Solves

Microservices add operational complexity (network latency, distributed tracing, deployment coordination) that a 3-week project with a small team cannot afford. A traditional layered monolith couples all code together, making testing and future extraction difficult. Modular Monolith provides the **discipline of microservices without the operational cost**.

## Module Structure

Every module follows the same three-layer convention:

```
module-name/
├── domain/               # Pure business logic — no Spring, no JPA, no frameworks
│   ├── model/            # Aggregates, entities, value objects
│   ├── service/          # Domain services (stateless business rules)
│   ├── event/            # Domain events (what happened)
│   └── port/             # Interfaces for repositories, notifiers, etc.
├── application/          # Use case orchestration
│   ├── service/          # Command/query handlers
│   ├── dto/              # Request/response objects
│   └── mapper/           # Domain <-> DTO mapping
└── infrastructure/       # Framework adapters
    ├── persistence/      # JPA repositories, entity mappings
    ├── messaging/        # Azure Service Bus publishers/consumers
    ├── web/              # REST controllers
    └── config/           # Spring configuration for this module
```

> The hexagonal package convention used by every module — `domain/` / `application/` / `port/` / `adapter/` with strict layer rules — is canonical in `architecture/03-hexagonal-architecture.md` §Package Convention.

## Modules and Their Responsibilities

| Module | Responsibility | Database Schema |
|--------|---------------|-----------------|
| **Transaction** | Receive, validate, and persist incoming transactions for evaluation | `oltp` (PostgreSQL) |
| **Case** | Manage fraud case lifecycle — creation, assignment, investigation, resolution | `cases` (PostgreSQL) |
| **Alert** | Generate and dispatch real-time fraud alerts to analysts | `alerts` (PostgreSQL) |
| **Reporting** | Serve analytics queries, dashboard data, and report exports | `reporting` (PostgreSQL, read-only role `reporting_reader`) |
| **Auth** | Handle authentication, authorization, and API key management | `auth` (PostgreSQL) |

> The Rule Engine is **not** an in-monolith module. It lives in its own extracted service — the *Serverless Engine* — per the qualification criteria in `architecture/05-selective-extraction.md`. It uses the same hexagonal layering and the same event contracts as the modules shown here.

> Storage justification for choosing a single PostgreSQL engine is consolidated in `decision-log/ADR-002-postgresql-only-db.md`.

## Communication Rules Between Modules

1. **No direct in-memory calls** between modules
2. All cross-module interaction uses **Domain Events** published to Azure Service Bus
3. A module can only trigger behavior in another module by publishing an event (including behaviour in an extracted service)
4. Each module owns its database schema exclusively (schema-per-module)

## Trade-offs

| Benefit | Trade-off |
|---------|-----------|
| Single deployment for the in-house workflow | Each extracted Service (Ingestion API, Serverless Engine, OCR Worker) deploys on its own revision |
| No network latency between in-monolith modules | Cross-deployment hops pay broker round-trip latency |
| Strong ACID consistency within a module | Only eventual consistency between modules and external services |
| Modules can be extracted later | Requires upfront discipline to maintain boundaries |
