# Selective Extraction Strategy

Start with all modules in the monolith, then extract specific modules into independent services **only when** there is a measurable need.

## What This Solves

Extracting modules prematurely adds operational cost (CI/CD pipelines, network latency, distributed debugging) without proven benefit. Selective Extraction ensures we only pay that cost when a module demonstrably needs independent scaling, a different runtime, or failure isolation.

## When a Module Qualifies for Extraction

A module is a candidate if it meets **two or more** of these:

| Criterion | Key Question | Example in Centinela |
|-----------|-------------|---------------------|
| **Independent scaling** | Does this module need different CPU/memory than the rest? | Ingestion API handles burst traffic |
| **Different technology** | Is a different runtime or language better suited? | OCR Worker needs Python for Azure AI SDK |
| **Independent deploy** | Does this module change on a different schedule? | Rule configs (weekly) vs core (monthly) |
| **Failure isolation** | Should this module stay up when others are down? | Ingestion must keep receiving during rule engine maintenance |
| **Team ownership** | Does a separate team own this module? | Not applicable (single team currently) |

## Currently Extracted Services

### Ingestion API

| Property | Detail |
|----------|--------|
| **Technology** | Spring Boot (Java 21) |
| **Why extracted** | Independent scaling for burst ingestion traffic |
| **Database** | PostgreSQL — `oltp` schema (the **Transaction module** owns writes here; the Ingestion API is a thin gateway into that schema). Read-only queries for Ingestion align with the Transaction module's exposed query API; Ingestion does not bypass it. |
| **Communication** | Persists transaction + outbox event (atomic). Outbox publisher drains to Azure Service Bus (`transactions-raw` queue). |
| **Failure tolerance** | Never depends on Core Backend availability at request-time. Even if Core Backend is offline, ingestion writes succeed; outbox holds events until broker recovers. |

### Document OCR Worker

| Property | Detail |
|----------|--------|
| **Technology** | FastAPI (Python 3.12) |
| **Why extracted** | Azure AI Document Intelligence SDK is Python-first |
| **Database** | None (stateless worker) |
| **Communication** | Consumes OCR task messages, publishes results to Service Bus |
| **Failure tolerance** | Tasks remain in queue if worker is down; processed when recovered |

## How to Extract a Module (Playbook)

1. **Verify boundaries** — ensure the module has no compile-time dependencies on other modules. Read `architecture/03-hexagonal-architecture.md`.
2. **Keep events** — cross-module communication already uses Service Bus; no changes needed. See `decision-log/ADR-003-async-messaging-reliability.md`.
3. **Extract database** — move the module's schema to its dedicated database or server if required. Schema-per-module isolation (`architecture/02-modular-monolith.md`) makes this a controller layer change.
4. **Containerize** — create Docker image and CI/CD pipeline (see [#8](https://github.com/Team-Centinela/Centinela-Code/issues/8)).
5. **Add resilience** — implement circuit breaker for the monolith-to-extracted-service calls.
6. **Route traffic** — add API gateway routing if the service needs its own HTTP endpoint.

> Authority: `decision-log/ADR-001-modular-monolith-hexagonal.md` defines the generous "Selective Extraction" criteria. Future extraction decisions consult that ADR before action.
