# Selective Extraction Strategy

Start with all modules in the monolith, then extract specific modules into independent services **only when** there is a measurable need.

## What This Solves

Extracting modules prematurely adds operational cost (CI/CD pipelines, network latency, distributed debugging) without proven benefit. Selective Extraction ensures we only pay that cost when a module demonstrably needs independent scaling, a different runtime, or failure isolation.

## When a Module Qualifies for Extraction

A module is a candidate if it meets **two or more** of these:

| Criterion | Key Question | Example in Centinela |
|-----------|-------------|---------------------|
| **Independent scaling** | Does this module need different CPU/memory than the rest? | Ingestion API and Serverless Engine both sustain distinct load profiles |
| **Different technology** | Is a different runtime or language better suited? | OCR Worker needs Python for Azure AI SDK |
| **Independent deploy** | Does this module change on a different schedule? | Rule configs (weekly) vs case management (monthly) |
| **Failure isolation** | Should this module stay up when others are down? | Ingestion must keep receiving during case management maintenance; scoring must remain available while case workflows change |
| **Team ownership** | Does a separate team own this module? | Not applicable (single team currently) |

## Currently Extracted Services

### Ingestion API

| Property | Detail |
|----------|--------|
| **Technology** | Spring Boot (Java 21) |
| **Why extracted** | Independent scaling for burst ingestion traffic |
| **Database** | PostgreSQL — `oltp` schema (the **Transaction module** owns writes here; the Ingestion API is a thin gateway into that schema). Read-only queries for Ingestion align with the Transaction module's exposed query API; Ingestion does not bypass it. |
| **Communication** | Persists transaction + outbox event (atomic). Outbox publisher drains to Azure Service Bus (`transactions-raw` queue). |
| **Failure tolerance** | Never depends on Core Backend or Serverless Engine availability at request-time. Even if both are offline, ingestion writes succeed; outbox holds events until broker recovers. |

### Serverless Engine (Rule Engine)

| Property | Detail |
|----------|--------|
| **Technology** | Spring Boot (Java 21) |
| **Why extracted** | Independent scaling for burst fraud-evaluation work driven by `transactions-raw` events; different deployment cadence (rule configs change weekly, case-management code monthly); failure isolation so a case-management regression cannot stall scoring. |
| **Runtime** | Azure Container Apps (Consumption plan) with **KEDA `azure-servicebus` scaler** bound to the `transactions-raw` queue — scales out per active-message count, scales to **zero replicas** when the queue is empty. Per-second billing. First 180,000 vCPU-seconds, 360,000 GiB-seconds, and 2 M requests / month / subscription are free. |
| **Database** | PostgreSQL — `oltp` and `rules_config` schemas. Scoring reads transactions + `rule_configs` and writes `transactions.triggered_rules` (`JSONB`) plus the outbox row for the `FraudEvaluationCompleted` event. Writes are co-owned with the Ingestion API under schema-per-module rules per `architecture/02-modular-monolith.md`. |
| **Communication** | Consumes `transactions-raw` (queue). Writes `transactions.triggered_rules` + outbox event in a single ACID transaction; the outbox publisher drains to the `case-events` topic carrying `FraudEvaluationCompleted`. |
| **Failure tolerance** | Idle, the Engine scales to zero replicas and accumulator cost is zero. A cold replica receives once-as-leased messages from `transactions-raw` (Service Bus lock-timeout redelivery handles the case). When ACAs cannot recover (rare), `outbox_events` rows in the engine's own database wait for restart of the consumer — the Ingestion API keeps accepting transactions in the meantime. |
| **Hexagonal discipline** | Same `domain/` / `application/` / `port/` / `adapter/` layout as an in-monolith module; the only difference is the shipping unit. See `architecture/03-hexagonal-architecture.md`. |

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
2. **Keep events** — cross-module communication already uses Service Bus; no changes needed. See `../decision-log/ADR-003-async-messaging-reliability.md`.
3. **Extract database** — move the module's schema to its dedicated database or server if required. Schema-per-module isolation (`architecture/02-modular-monolith.md`) makes this a controller layer change.
4. **Containerize** — create Docker image and CI/CD pipeline (see [#8](https://github.com/Team-Centinela/Centinela-Code/issues/8)).
5. **Wire the runtime** — for a Spring Boot service that triggered extraction, target Azure Container Apps Consumption with a KEDA scaler bound to the inbound queue (`azure-servicebus` scaler for queue/topic consumers, `http` scaler for REST APIs). The `infrastructure/README.md` §Service Bus ownership mirrors the queue/topic wiring so IaC can enforce the contract.
6. **Add resilience** — implement the Outbox Pattern (mandatory per ADR-003 §3.2) and consumer-side idempotency (`../patterns/06-idempotency-key.md`). Cold-start of an Azure Container Apps replica is a sub-second event for our workloads; no extra circuit breaker is required at the queue boundary.
7. **Route traffic** — add API gateway routing only if the service needs its own HTTP endpoint (e.g., the Ingestion API's `POST /api/v1/transactions`); KEDA handles the event-driven services.

> Authority: `../decision-log/ADR-001-modular-monolith-hexagonal.md` defines the generous "Selective Extraction" criteria; the Serverless Engine is the third service extracted under those criteria. Future extraction decisions consult that ADR before action.

## Per-lane Ownership of the Azure Surface

Per [ADR-010](../decision-log/ADR-010-issue-pr-discipline.md) and the [Role & Responsibility Matrix](../best-practices/05-pr-and-issue-discipline.md) §"Role & Responsibility Matrix", the Azure surface of each extracted service is owned in the **same lane** as the code surface:

| Service | Lane (per ADR-010) | Azure parcel files |
|---|---|---|
| **Ingestion API** | B (Owner: @3105jero) | `infrastructure/services/ingestion/main.tf` — `azurerm_container_app.ingestion`, identity `SystemAssigned`, secret refs to Key Vault, env vars for SB and DB |
| **Serverless Engine** | C (Owner: @SebastianT2006) | `infrastructure/services/serverless-engine/main.tf` — `azurerm_container_app.serverless_engine`, KEDA `azure-servicebus` scaler on `transactions-raw`, identity, secret refs |
| **Core Backend** | D (Owner: @JjuanGarcia77) | `infrastructure/services/core-backend/main.tf` — `azurerm_container_app.core_backend`, identity, case-events topic subscriber binding |
| **OCR Worker** | TBD Sprint 3 (Lane E carries forward) | `infrastructure/services/ocr-worker/main.tf` |
| **Shared platform** | E (Owner: @Santiagodxz) | `infrastructure/modules/platform/` — Service Bus Standard, Key Vault, Log Analytics, ACA Environment, Budget automation. One-time shared IaC consumed by all lanes. |
| **Frontend** | E (Owner: @Santiagodxz) | `infrastructure/services/frontend/main.tf` — SWA. |

> **Why this matters:** a code PR that flips an env var, a secret ref, or a queue topology carries an `azure-impact: yes` label and is paired with an `infra`-labelled companion in **the same lane** (per ADR-010 §10.3 — Companion infra issue convention). Reviewers scoped to that lane are sufficient; the platform lane is consulted only for shared modules.
