# Serverless Engine (Rule Engine)

> **Status:** directory placeholder — the Maven module is not yet registered in `services/pom.xml`. Tracking issue: **#51** (`[services] Register serverless-engine Maven module in services/pom.xml`). Until #51 closes, this directory is informational only.

Pulled out per **ADR-001 §Decision #4** as the third extracted service. Lives in its own Spring Boot application on Azure Container Apps (Consumption) with a **KEDA `azure-servicebus` scaler** bound to the `transactions-raw` queue. Per `../../docs/architecture/05-selective-extraction.md`, this service is the *only* deployment unit for the Pipeline Pattern defined in `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md`.

## Why it is extracted

- **Independent scaling** — burst fraud-evaluation work driven by `transactions-raw` events needs its own CPU/memory profile distinct from Case/Alert/Reporting in the Core Backend.
- **Technology fit** — Spring Boot 3.4.x on JDK 21 is the same stack as Ingestion API and Core Backend; one hexagonal discipline (`../../docs/architecture/03-hexagonal-architecture.md`), one deployment unit difference; one binder per ADR-003 §3.1.
- **Failure isolation** — a regression in case-management code must not stall scoring (and vice versa).
- **Independent deploy** — `rule_configs` data changes weekly; the Pipeline code changes monthly; the Core Backend changes on a different cadence.

## What it owns (when #51 closes and the module is wired)

| Capability | Backing |
|---|---|
| Consume `transactions-raw` queue from Azure Service Bus | `spring-cloud-azure-starter-servicebus` binder adapter (`com.azure.spring` 5.19.0) per ADR-003 §3.1 |
| Run the two-stage Pipeline (`STAGE 1`: FR-1 Velocity + FR-4 High-Risk Merchant; `STAGE 2`: FR-3 Impossible Geo + FR-2 Atypical Amount) | `FraudPipeline` orchestrator per `../../docs/patterns/04-pipeline-pattern.md` |
| Persist `transactions.triggered_rules` (JSONB) plus outbox event in one ACID tx | `outbox.outbox_events` per `../../docs/patterns/03-outbox-pattern.md` |
| Emit `FraudEvaluationCompleted` to the `case-events` topic | Service Bus publisher; topic awareness via the same binder |
| Deterministic NL Explainer (no LLM) | `RuleExplanationTemplates.java` per ADR-004 §4.2 and `../../docs/patterns/04-pipeline-pattern.md` §`rawEvidence` Schema |
| Own health + readiness endpoints | `/actuator/health/outbox-lag` per ADR-003 §3.2 |

## What it does **not** own

- Ingestion of transactions (lives in the **Ingestion API** at `services/ingestion/`).
- Case lifecycle, alerting, reporting, auth, admin endpoints (lives in the **Core Backend** at `services/core-backend/`).
- Document OCR (lives in the **OCR Worker** at `services/ocr-worker/`).

## Implementation references

- Pipeline ordering / stage assignment / short-circuit semantics: `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md` §4.1 / §4.4.
- Per-FR `rawEvidence` JSONB schema: `../../docs/patterns/04-pipeline-pattern.md` §`rawEvidence` Schema.
- Idempotency on the `transactions-raw` consumer: `../../docs/patterns/06-idempotency-key.md`.
- Outbox lifecycle / cold-start handling: ADR-003 §3.2 + `../../docs/patterns/03-outbox-pattern.md` §Behavior under planned PostgreSQL downtime.

## Owned GitHub Issues

- **Implementation:** #42 — Implement Rule Engine Pipeline (FR-1..FR-4). **Blocked by** #51 (this directory's `<module>` registration).
- **Implementation:** #40 — Outbox Publisher with shutdown recovery (serverless-engine's own publisher per ADR-003 §3.2).
- **Implementation:** #41 — Consumer-side idempotency for the `transactions-raw` consumer.

## File layout (target)

```
serverless-engine/
├── pom.xml                              # child of services/pom.xml (issue #51)
├── README.md
├── Dockerfile                           # ACA-compatible; JVM 21 eclipse-temurin
├── src/main/java/com/centinela/serverless/
│   ├── Application.java                 # @SpringBootApplication
│   ├── domain/                          # Aggregates (FraudScore, RuleResult, TriggeredRule)
│   │   ├── model/
│   │   └── service/
│   ├── application/                     # Use case: ScoreTransaction port
│   ├── port/                            # Outbound ports: RuleConfigRepo, FraudCasePublisher (outbox-bound)
│   └── adapter/
│       ├── persistence/                 # JPA implementations
│       ├── messaging/                   # Service Bus consumer (transactions-raw), publisher (outbox → case-events)
│       └── rest/                        # (none — no REST surface; inbound is queue-only)
└── src/test/java/com/centinela/serverless/
    └── ...                              # Unit + integration tests (per ../../docs/best-practices/02-testing-strategy.md)
```

Per **ADR-001 §Action Plan**, the parent-POM registration (#51) blocks any source code in this directory from being built. Until #51 closes, this file documents intent only.
