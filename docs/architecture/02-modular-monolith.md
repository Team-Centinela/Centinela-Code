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

## Modules and Their Responsibilities

| Module | Responsibility | Database Schema |
|--------|---------------|-----------------|
| **Transaction** | Receive, validate, and persist incoming transactions for evaluation | `oltp` (PostgreSQL) |
| **Rule Engine** | Evaluate fraud rules (velocity, outlier, geo, blacklist), calculate risk scores | `rules_config` (PostgreSQL) |
| **Case** | Manage fraud case lifecycle — creation, assignment, investigation, resolution | `cases` (PostgreSQL) |
| **Alert** | Generate and dispatch real-time fraud alerts to analysts | `alerts` (PostgreSQL) |
| **Reporting** | Serve analytics queries, dashboard data, and report exports | `reporting` (PostgreSQL read-replica) |
| **Auth** | Handle authentication, authorization, and API key management | `auth` (PostgreSQL) |

> Storage justification for choosing a single PostgreSQL engine is consolidated in `decision-log/ADR-002-postgresql-only-db.md`.

## Communication Rules Between Modules

1. **No direct in-memory calls** between modules
2. All cross-module interaction uses **Domain Events** published to Azure Service Bus
3. A module can only trigger behavior in another module by publishing an event
4. Each module owns its database schema exclusively (schema-per-module)

## Trade-offs

| Benefit | Trade-off |
|---------|-----------|
| Single deployment, simple CI/CD | Entire application deploys even for single-module changes |
| No network latency between modules | All modules share the same JVM and resources |
| Strong ACID consistency within a module | Only eventual consistency between modules |
| Modules can be extracted later | Requires upfront discipline to maintain boundaries |
