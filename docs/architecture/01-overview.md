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

The five deployable artifacts (full table in [`services/README.md`](../../services/README.md)) share a single PostgreSQL Flexible Server, one Service Bus namespace, one Blob Storage account, one Key Vault, and one Application Insights workspace — operational cohesion without coupling between deployments. Compute substrate: all four backends on Azure Container Apps Consumption (ADR-009); frontend on Azure Static Web Apps Free.

**Local development + Phase 0 pre-validation** run against the Docker Compose emulator stack (PostGIS + Microsoft Service Bus Emulator + Floci-AZ + Azure SQL Edge + the three Spring Boot services on the `local-emulator` profile) per [ADR-011](../decision-log/ADR-011-local-emulator-stack.md). Real Azure is the deploy target of merged code, not a test surface — Phase 0 catches ~92 % of post-merge regressions on $0 emulators before consuming Azure budget. Verify gate: `scripts/verify-emulators.{ps1,sh}` returns `18 PASS / 0 FAIL`.

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

Real-time fraud detection is the user-facing promise (§ASSIGNMENT.md §1.2). The $60/21-day budget (§ASSIGNMENT.md §3) is the *cost-care* promise. The architecture reconciles them by treating real-time as an **active-period** invariant, not a 24/7 one: Container Apps scale to zero at idle (ADR-009), PostgreSQL auto-stops after 1h idle (ADR-002), and the Outbox Pattern drains any backlog safely on restart (`../patterns/03-outbox-pattern.md` §"Behavior under planned PostgreSQL downtime"). Consumer-side idempotency (`../patterns/06-idempotency-key.md`) absorbs the post-restart burst. Full operational posture: `../patterns/03-outbox-pattern.md` §"Behavior under planned PostgreSQL downtime (B1ms auto-stop)" and `../decision-log/ADR-002-postgresql-only-db.md` §"Planned DB downtime & outbox restart-drain".

## Lane-Ownership Overlay

The architecture above is staffed by **five parallel lanes** (per [`../best-practices/05-pr-and-issue-discipline.md`](../best-practices/05-pr-and-issue-discipline.md) §"Role & Responsibility Matrix"). Every service has both a *code* lane and a *per-service Azure* lane; one shared platform lane builds cross-cutting substrate.

| Lane | Service | Code owner | Azure parcel owner |
|---|---|---|---|
| **A** | Governance | @SrLampi1001 | (n/a) |
| **B** | Ingestion | @3105jero | @3105jero |
| **C** | Serverless Engine + plumbing | @SebastianT2006 | @SebastianT2006 |
| **D** | Core Backend + modular monolith scaffold | @JjuanGarcia77 | @JjuanGarcia77 |
| **E** | Platform (outbox-starter, observability-starter, test-support, OpenAPI consolidation, CI) + frontend | @Santiagodxz | @Santiagodxz |

Every code-touching change that flips a deployment surface travels with a paired `infra`-labelled companion issue per [ADR-010](../decision-log/ADR-010-issue-pr-discipline.md); the companion carries its `EXPECTED DELIVERY` and `COST-ATTRIBUTION` blocks ([`../patterns/07-azure-impact-companion-issue.md`](../patterns/07-azure-impact-companion-issue.md)).

## Related Documents

- [ADR-001: Modular Monolith + Hexagonal Architecture](../decision-log/ADR-001-modular-monolith-hexagonal.md)
- [ADR-002: Unified PostgreSQL Persistence](../decision-log/ADR-002-postgresql-only-db.md)
- [ADR-003: Async Messaging & Reliability](../decision-log/ADR-003-async-messaging-reliability.md)
- [ADR-004: Rule Engine — Pipeline Pattern](../decision-log/ADR-004-rule-engine-pipeline-explainer.md)
- [ADR-005: Monorepo Unification](../decision-log/ADR-005-monorepo-unification.md)
- [ADR-010: Issue & PR Discipline](../decision-log/ADR-010-issue-pr-discipline.md)
- [Best-Practices 05 — PR & Issue Discipline](../best-practices/05-pr-and-issue-discipline.md)
- [Pattern 07 — Azure Impact Companion Issue](../patterns/07-azure-impact-companion-issue.md)
- [ADR-011: Local Emulator Stack for Pre-Validation](../decision-log/ADR-011-local-emulator-stack.md)
- [Technology Stack](06-technology-stack.md)
- [Hexagonal Architecture](03-hexagonal-architecture.md)
- [Selective Extraction](05-selective-extraction.md)
- [Outbox Pattern — Behavior under planned PostgreSQL downtime](../patterns/03-outbox-pattern.md#behavior-under-planned-postgresql-downtime-b1ms-auto-stop)
