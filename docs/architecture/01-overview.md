# Architecture Overview

Centinela uses a **Modular Monolith with Hexagonal Architecture** as the deployment posture for case-management logic, surrounded by **selectively-extracted services** for the workloads that have independent scaling or isolation needs. Each deployment unit — monolith or extracted service — is internally organized in hexagonal layers and communicates via domain events over Azure Service Bus.

## What This Architecture Achieves

- **Domain isolation** — fraud detection logic lives in pure Java, decoupled from frameworks and infrastructure
- **Independent modules** — Transaction, Case, Alert, Reporting, and Auth own their data and logic inside the monolith; Rule Engine (scoring) and OCR are deployed as their own services, each with the same hexagonal discipline
- **Serverless where it matters** — the Rule Engine and Ingestion API run on Azure Container Apps Consumption plan with KEDA Service Bus / HTTP scaling; cost is paid per active vCPU-second, not for idle clusters
- **Future-proof extraction** — any module can be extracted to an independent microservice without rewriting business logic
- **Operational simplicity** — one build pipeline, four small deployments, no service mesh overhead

## System Components

```
                    ┌─────────────────────────────────────────────┐
                    │              Modular Monolith                │
                    │  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
                    │  │Transaction│  │   Case   │  │  Alert   │   │
                    │  │  Module   │  │  Module  │  │  Module  │   │
                    │  └───────────┘  └──────────┘  └──────────┘   │
                    │  ┌──────────┐  ┌──────────┐                    │
                    │  │Reporting │  │   Auth   │                    │
                    │  │  Module  │  │  Module  │                    │
                    │  └──────────┘  └──────────┘                    │
                    └─────────────────────┬─────────────────────────┘
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              │                           │                           │
      ┌───────┴────────┐          ┌────────┴────────┐          ┌──────┴───────────┐
      │ Azure Service  │          │ Azure Service  │          │ Azure Service      │
      │ Bus            │          │ Bus            │          │ Bus                │
      │ (case-events,  │          │ (transactions- │          │ (documents-        │
      │  documents-    │          │  raw)          │          │  pending)          │
      │  pending-pub)  │          │                │          │                    │
      └───────┬────────┘          └────────┬────────┘          └──────┬────────────┘
              │                           │                           │
              │                           ▼                           ▼
              │                ┌─────────────────────┐         ┌──────────────┐
              │                │  Serverless Engine  │         │ OCR Worker   │
              │                │  (Rule Engine)      │         │ (FastAPI /   │
              │                │  Spring Boot on ACA │         │  Python)     │
              │                │  Consumption + KEDA │         └──────────────┘
              │                └─────────────────────┘                 ▲
              │                                                          │
              └──────────────────────────────────────────────────────────┘
                                  (cross-deployment events)
                                          ▲
                                          │
                                  ┌───────┴────────┐
                                  │ Ingestion API  │
                                  │ (Spring Boot   │
                                  │  on ACA        │
                                  │  Consumption)  │
                                  └────────────────┘
```

The four **deployable artifacts** of the platform:

| Artifact | Type | Deployment |
|---|---|---|
| **Ingestion API** | Spring Boot (Java 21) | Azure Container Apps (Consumption), HTTP-scaled |
| **Serverless Engine** (Rule Engine) | Spring Boot (Java 21) | Azure Container Apps (Consumption), KEDA-scaled on `transactions-raw` |
| **Core Backend** | Spring Boot (Java 21) modular monolith | Azure Container Apps (Consumption), HTTP-scaled, hosts Case / Alert / Reporting / Auth / Transaction modules |
| **OCR Worker** | FastAPI (Python 3.12) | Azure Container Apps (Consumption), KEDA-scaled on `documents-pending` |
| **Frontend** | React + TypeScript (Vite SPA) | Azure Static Web Apps |

All four backends share a single PostgreSQL Flexible Server, one Service Bus namespace, one Blob Storage account, one Key Vault, and one Application Insights workspace — operational cohesion without coupling between deployments.

## Principles That Guide Decisions

| Principle | What It Means |
|-----------|---------------|
| **Domain-Centric** | Business logic is framework-agnostic; infrastructure is an adapter |
| **Module Isolation** | Each module owns its schema and logic; no shared databases or direct calls |
| **Event-Driven** | Modules coordinate asynchronously via Azure Service Bus domain events |
| **Selective Extraction** | Modules stay in the monolith by default; extract only when independent scaling, technology, or isolation demands it |
| **Serverless by Default** | Every deployable artifact runs on Azure Container Apps Consumption — pay per active vCPU-second, scale to zero on idle; no AKS / managed Kubernetes |
| **Testability** | Pure domain logic can be unit-tested without Spring, databases, or network |

## Operational Posture

Real-time fraud detection is the user-facing promise (§ASSIGNMENT.md §1.2). The 21-day, $60 budget constraint (§ASSIGNMENT.md §3) is the *cost-care* promise. The architecture reconciles them by treating real-time as an **active-period** invariant, not a 24/7 one:

| Concern | Active period (when the team is testing) | Quiet period (weekday nights + weekends) |
|---|---|---|
| **Compute** | All four Container Apps at ≥1 replica; KEDA-driven scale-out for the Serverless Engine and OCR Worker. | Container Apps scale to zero replicas. **No compute cost.** |
| **Database** | B1ms PostgreSQL Flexible Server **running**. CRUD + Outbox Publisher work normally. | B1ms PostgreSQL Flexible Server **stopped** via Azure Automation runbook (`#10`). Ingestion API returns `503` + `Retry-After` until next planned start. |
| **Outbox Publisher** | Drains `outbox_events` every 1 s (`patterns/03-outbox-pattern.md`). | Paused (DB offline). Restarts on the first poll after `start` and drains the backlog in seconds. |
| **Service Bus** | Standard tier, always on (≈$7/21 d; required for Topics). | Standard tier, always on. Messages remain on the broker; consumers are idle. |

The Outbox Pattern is **safe across planned DB restarts** because no events can be inserted while the DB is offline (the writer's `BEGIN` fails). On restart, the Outbox Publisher's existing `SELECT … WHERE status='PENDING'` enumerates every backlog row and drains it. Consumer-side idempotency (`patterns/06-idempotency-key.md`) absorbs the post-restart burst. Detailed phase-by-phase table: `patterns/03-outbox-pattern.md` §"Behavior under planned PostgreSQL downtime". See `decision-log/ADR-002-postgresql-only-db.md` §"Planned DB downtime & outbox restart-drain".

## Related Documents

- [ADR-001: Modular Monolith + Hexagonal Architecture](../decision-log/ADR-001-modular-monolith-hexagonal.md)
- [ADR-002: Unified PostgreSQL Persistence](../decision-log/ADR-002-postgresql-only-db.md)
- [ADR-003: Async Messaging & Reliability](../decision-log/ADR-003-async-messaging-reliability.md)
- [ADR-004: Rule Engine — Pipeline Pattern](../decision-log/ADR-004-rule-engine-pipeline-explainer.md)
- [ADR-005: Monorepo Unification](../decision-log/ADR-005-monorepo-unification.md)
- [Technology Stack](06-technology-stack.md)
- [Hexagonal Architecture](03-hexagonal-architecture.md)
- [Selective Extraction](05-selective-extraction.md)
- [Outbox Pattern — Behavior under planned PostgreSQL downtime](../patterns/03-outbox-pattern.md#behavior-under-planned-postgresql-downtime-b1ms-auto-stop)
