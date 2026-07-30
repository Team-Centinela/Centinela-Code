# Serverless Engine (Rule Engine)

> **Status:** active module — registered in `services/pom.xml` as child of `centinela-parent` (closes #51). All dependency versions are inherited from the parent POM single source of truth per ADR-001 §"Decision #4", ADR-003 §3.1, ADR-009 §9.1. Consumer + idempotency + outbox emission closed by PR #130 follow-up commits (`ad69020` ... `7399dd1` on `feature/issue-54-serverless-engine`).

Pulled out per **ADR-001 §Decision #4** as the third extracted service. Lives in its own Spring Boot application on Azure Container Apps (Consumption) with a **KEDA `azure-servicebus` scaler** bound to the `transactions-raw` queue. Per `../../docs/architecture/05-selective-extraction.md`, this service is the *only* deployment unit for the Pipeline Pattern defined in `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md`.

## Why it is extracted

- **Independent scaling** — burst fraud-evaluation work driven by `transactions-raw` events needs its own CPU/memory profile distinct from Case/Alert/Reporting in the Core Backend.
- **Technology fit** — Spring Boot 3.4.x on JDK 21 is the same stack as Ingestion API and Core Backend; one hexagonal discipline (`../../docs/architecture/03-hexagonal-architecture.md`), one deployment unit difference; one binder per ADR-003 §3.1.
- **Failure isolation** — a regression in case-management code must not stall scoring (and vice versa).
- **Independent deploy** — `rule_configs` data changes weekly; the Pipeline code changes monthly; the Core Backend changes on a different cadence.

## What it owns (with #51 closed and the engine wired end-to-end)

| Capability | Backing | Implemented by |
|---|---|---|
| Consume `transactions-raw` queue from Azure Service Bus | `adapter/in/consumer/TransactionsRawConsumer.java` (functional `Consumer<Message<String>>` bean + spring-cloud-azure-servicebus binder per ADR-003 §3.1, ADR-009 §9.1) | [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| Run the two-stage Pipeline (`STAGE 1`: FR-1 Velocity + FR-4 High-Risk Merchant; `STAGE 2`: FR-3 Impossible Geo + FR-2 Atypical Amount) | `domain/service/FraudPipeline.java` orchestrator per `../../docs/patterns/04-pipeline-pattern.md`; rules under `domain/service/*Rule.java` | [PR #123](https://github.com/Team-Centinela/Centinela-Code/pull/123), [PR #124](https://github.com/Team-Centinela/Centinela-Code/pull/124), [PR #125](https://github.com/Team-Centinela/Centinela-Code/pull/125) |
| Persist `triggered_rules.triggered_rules` JSONB audit row plus outbox event in one ACID tx | `adapter/out/persistence/JpaTriggeredRuleRepository.java` + `infrastructure/outbox/JpaOutboxEventAppender.java` per ADR-003 §3.2 | [PR #126](https://github.com/Team-Centinela/Centinela-Code/pull/126), [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| Emit `FraudEvaluationCompleted` to the `case-events` topic | `infrastructure/outbox/EngineOutboxPublisher.java` drives `infrastructure/outbox/EngineServiceBusPublisherImpl.java` (StreamBridge) | [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| Persist & advance the consumer-side idempotency ledger on `received_messages` | `infrastructure/idempotency/ReceivedMessageIdempotencyService.java` + `ReceivedMessageRepository.java` per ADR-003 §3.3.2 + `../../docs/patterns/06-idempotency-key.md` §3.3.2 | [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| W3C TraceContext propagation across the broker hop | `infrastructure/observability/TraceparentPropagator.java` per ADR-007 §7.2 | [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| Emit ADR-007 §7.4 business metrics | `centinela.transactions.evaluated{result}`, `centinela.rule.triggered{rule_code,result}`, `centinela.evaluation.duration_ms{stage}` from `application/ScoreTransactionService.java` | [PR #123](https://github.com/Team-Centinela/Centinela-Code/pull/123), [PR #124](https://github.com/Team-Centinela/Centinela-Code/pull/124), [PR #125](https://github.com/Team-Centinela/Centinela-Code/pull/125), [PR #126](https://github.com/Team-Centinela/Centinela-Code/pull/126), [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| Own health + readiness endpoints | `/actuator/health/outbox-lag` per ADR-003 §3.2 | [PR #130](https://github.com/Team-Centinela/Centinela-Code/pull/130) |
| ArchUnit hexagonal-layering enforcement | `archunit/ServerlessEngineArchitectureTest.java` per ADR-001 §Hexagonal | [PR #128](https://github.com/Team-Centinela/Centinela-Code/pull/128) |

Cited PRs (per [#166 §1.13](https://github.com/Team-Centinela/Centinela-Code/issues/166) / [#168 §1.4](https://github.com/Team-Centinela/Centinela-Code/issues/168) Phase 1.4 close-out): **#123, #124, #125, #126, #128, #130**. Audit-trail receipts per #160 (closing comments) + #161 (`[Lane-E][E.45]` ArchUnit extraction) + #222 (`[C.Post-Review]` parent of the 31 §15 audit findings).

## What it does **not** own

- Ingestion of transactions (lives in the **Ingestion API** at `services/ingestion/`).
- Case lifecycle, alerting, reporting, auth, admin endpoints (lives in the **Core Backend** at `services/core-backend/`).
- Document OCR (lives in the **OCR Worker** at `services/ocr-worker/`).
- NL Explainer templates — these live in `infrastructure/explainer/templates/RuleExplanationTemplates.java` once @3105jero's templates PR lands (ADR-004 §4.2 evidence-key contract is set; templates are the consumer).

## Implementation references

- Pipeline ordering / stage assignment / short-circuit semantics: `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md` §4.1 / §4.4.
- Per-FR `rawEvidence` JSONB schema (pinned): `../../docs/decision-log/ADR-004-rule-engine-pipeline-explainer.md` §4.2. Validated by `adapter/out/persistence/RawEvidenceValidator.java` at construction time so a future implementation that drifts surfaces in tests.
- Idempotency on the `transactions-raw` consumer (ADR-003 §3.3.2 strategy): `../../docs/patterns/06-idempotency-key.md` §3.3.2.
- Outbox lifecycle / cold-start handling: ADR-003 §3.2 + `../../docs/patterns/03-outbox-pattern.md` §Behavior under planned PostgreSQL downtime.
- Business metrics emission: ADR-007 §7.4.

## Owned GitHub Issues

- **Registration:** #51 / #88 — Maven module registered in `services/pom.xml` (**CLOSED**)
- **Implementation (epic):** #54 — Serverless Engine complete (Sebastián's plumbing half closed by PR #130 + follow-up commits; @3105jero's rule-pipeline half shipped across PRs #122-#126 and #128)
- **Outbox publisher:** #40 — closed by PR #120 (cross-service) + the engine-local `EngineOutboxPublisher` here
- **Consumer idempotency:** #41 — closed for the engine side; needs follow-up for Core Backend + Ingestion

## File layout (actual, as of `7399dd1`)

```
serverless-engine/
├── pom.xml                              # child of services/pom.xml (#51 / #88)
├── README.md                            # this file
└── src/
    ├── main/
    │   ├── java/com/centinela/serverless/
    │   │   ├── ServerlessEngineApplication.java          # @SpringBootApplication, @EnableScheduling
    │   │   ├── adapter/
    │   │   │   ├── in/consumer/
    │   │   │   │   └── TransactionsRawConsumer.java     # ADR-003 §3.1 binder consumer
    │   │   │   └── out/persistence/
    │   │   │       ├── JpaTriggeredRuleRepository.java        # port impl for TriggeredRuleRepository
    │   │   │       ├── SpringDataTriggeredRuleRepository.java  # Spring Data interface
    │   │   │       ├── TriggeredRuleEntity.java                # JPA mapping for triggered_rules.triggered_rules
    │   │   │       ├── RawEvidenceValidator.java                # rejects rawEvidence missing ADR-004 §4.2 keys
    │   │   │       ├── InMemoryRuleConfigRepository.java        # TODO(@3105jero,epic #54): JPA impl
    │   │   │       ├── InMemoryTransactionStatisticsRepository.java
    │   │   │       ├── InMemoryTransactionStatsRepository.java
    │   │   │       └── InMemoryFlaggedMerchantRepository.java
    │   │   ├── application/
    │   │   │   └── ScoreTransactionService.java        # @Transactional orchestrator + ADR-007 §7.4 metrics
    │   │   ├── domain/
    │   │   │   ├── event/
    │   │   │   │   └── TransactionReceivedEvent.java   # Service Bus inbound event shape
    │   │   │   ├── model/
    │   │   │   │   ├── FraudDecision.java              # FraudPipeline return shape
    │   │   │   │   ├── Recommendation.java             # APPROVE | FLAG | BLOCK (case-creation)
    │   │   │   │   └── TriggeredRule.java              # record(ruleCode, score, evidence, evaluatedAt)
    │   │   │   ├── port/
    │   │   │   │   ├── FlaggedMerchant.java
    │   │   │   │   ├── FlaggedMerchantRepository.java  # FR-4 port
    │   │   │   │   ├── OutboxEventAppender.java         # FR-2/3/4 + outbox binding
    │   │   │   │   ├── RuleConfig.java
    │   │   │   │   ├── RuleConfigRepository.java        # operator-tunable FR params
    │   │   │   │   ├── TransactionStatisticsRepository.java  # FR-1 port
    │   │   │   │   ├── TransactionStats.java
    │   │   │   │   ├── TransactionStatsRepository.java     # FR-2 port
    │   │   │   │   └── TriggeredRuleRepository.java         # FR-* audit row port
    │   │   │   └── service/
    │   │   │       ├── AggregatorStage.java            # PipelineStage + final Recommendation
    │   │   │       ├── AtypicalAmountRule.java          # FR-2
    │   │   │       ├── EvaluationContext.java           # per-message state for the pipeline
    │   │   │       ├── FraudPipeline.java              # two-stage orchestrator
    │   │   │       ├── HighRiskMerchantRule.java       # FR-4
    │   │   │       ├── ImpossibleGeoRule.java          # FR-3
    │   │   │       ├── PipelineStage.java               # interface
    │   │   │       └── VelocityRule.java               # FR-1
    │   │   └── infrastructure/
    │   │       ├── configuration/
    │   │       │   └── RulePipelineWiring.java         # Spring @Configuration wiring rules + repos into FraudPipeline
    │   │       ├── idempotency/
    │   │       │   ├── IdempotencyDialectResolver.java
    │   │       │   ├── ReceivedMessageIdempotencyService.java  # ADR-003 §3.3.2
    │   │       │   └── ReceivedMessageRepository.java
    │   │       ├── observability/
    │   │       │   └── TraceparentPropagator.java      # W3C trace context across Service Bus (ADR-007 §7.2)
    │   │       └── outbox/
    │   │           ├── EngineOutboxEventEntity.java
    │   │           ├── EngineOutboxEventJpaRepository.java
    │   │           ├── EngineOutboxPublisher.java        # @Scheduled drain, ADR-003 §3.2 retry/DLQ
    │   │           ├── EngineServiceBusPublisher.java
    │   │           ├── EngineServiceBusPublisherImpl.java   # StreamBridge binding
    │   │           └── JpaOutboxEventAppender.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       └── db/migration/common/V1__init_engine_schemas.sql
    └── test/
        └── java/com/centinela/serverless/
            ├── adapter/
            │   ├── in/consumer/TransactionsRawConsumerTest.java
            │   └── out/persistence/TriggeredRulePersistenceTest.java
            ├── archunit/ServerlessEngineArchitectureTest.java
            ├── domain/
            │   ├── event/TransactionReceivedEventTest.java
            │   └── service/
            │       ├── AggregatorStageTest.java
            │       ├── AtypicalAmountRuleTest.java
            │       ├── FraudPipelineTest.java
            │       ├── HighRiskMerchantRuleTest.java
            │       ├── ImpossibleGeoRuleTest.java
            │       └── VelocityRuleTest.java
            ├── infrastructure/idempotency/ReceivedMessageIdempotencyServiceUnitTest.java
            └── ServerlessEngineApplicationTest.java
```

Per **ADR-001 §Action Plan**, the parent-POM registration (#51 / #88) blocked any source code in this directory from being built until they were closed; with both closed, the active service contract is documented here. Per **AGENTS.md §"service README imprint rule"**, this README is kept current with each commit that adds or removes a deployable surface in `serverless-engine/`.

### Hexagonal layering (enforced by `archunit/ServerlessEngineArchitectureTest.java`)

- `domain/` has zero Spring / JPA / Azure imports (ADR-001 §Hexagonal).
- `domain/` does not depend on `application/` or `adapter/` (innermost ring).
- `application/` orchestrates domain services through ports; may use Spring framework annotations.
- `adapter/` is the outermost ring; concrete Spring/JPA/Azure wiring.
- `infrastructure/configuration/` is for `@Configuration` beans that bridge Spring into the domain (e.g., `RulePipelineWiring`); it is NOT in the domain because it imports concrete adapters.

## Operational notes

- **Cold start** — first message after idle triggers KEDA on `transactions-raw`, ACA spins a replica; first evaluation runs in ~1-2 s including the outbox poll timer init.
- **Scale to zero** — `replicas=0` at idle, `$0` compute per ADR-009 §9.3.
- **DB auto-stop** — PostgreSQL B1ms auto-stops after 1 h idle (ADR-002). The outbox publisher's `@PostConstruct` ping (not yet wired here — see commit `13a429b`'s resetStalePending TODO) is the next layer; for now Operator should expect a ~30 s cold-down + cold-up penalty.
- **Cost** — see `../../infrastructure/README.md` cost table; this service is counted under `centinela-serverless-engine` ACA Consumption.
- **Recovery / DR** — see `../../docs/patterns/03-outbox-pattern.md` §"Behavior under planned PostgreSQL downtime".

## Local development

This service ships with a `local-emulator` Spring profile (`src/main/resources/application-local-emulator.yml`) that points at the Docker Compose emulator stack from `docker-compose.yml` — PostGIS at `centinela-postgres:5432` and the Microsoft Service Bus Emulator at `centinela-servicebus:5672`. The full local surface (PostgreSQL + Service Bus + Key Vault + Blob + App Insights, via Floci-AZ) is governed by [`../../docs/decision-log/ADR-011-local-emulator-stack.md`](../../docs/decision-log/ADR-011-local-emulator-stack.md).

```sh
# From repo root
cp .env.example .env
docker compose up -d --wait                  # boots 4 emulators + 3 services
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-emulators.ps1
# Expected: 18 PASS / 0 FAIL

# Or run the engine alone against the running stack
SPRING_PROFILES_ACTIVE=local-emulator \
  AZURE_SERVICEBUS_CONNECTION_STRING="Endpoint=sb://centinela-servicebus:5672;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;" \
  mvn -pl services/serverless-engine spring-boot:run
```

**Never** enable the `local-emulator` profile against a real Azure Service Bus namespace or B1ms PostgreSQL — the hardcoded SAS key + local Postgres creds will fail loudly (immediate startup exception), per ADR-011 §11.4 + PR #171 §Production Safety contract. A lint rule gating this profile to local-only is tracked as `[E.48]` #108.

## Follow-up TODOs (cross-reference for the next @3105jero / @SebastianT2006 sprint)

1. Replace the four `InMemory*Repository` stubs with JPA impls against `oltp.transactions`, `oltp.flagged_merchants`, etc. (epic #54 evidence-persistence half).
2. Wire `EngineOutboxEventJpaRepository.resetStalePending(...)` into a `@Scheduled` task to advance ADR-003 §3.2's recovery story.
3. Add a Testcontainers-backed integration test (`@Testcontainers + PostgreSQL + Service Bus emulator`) so the §3.3.2 dialect-resolver path is exercised end-to-end (commit `5440c24` notes this as deferred).
4. Add `RuleExplanationTemplates.java` (deterministic NL Explainer per ADR-004 §4.2) — once present, the explainer can read the now-pinned evidence keys.
5. Spring Cloud Stream `auto-startup: true` is currently `false` to keep init fast in non-prod profiles (set explicitly via ACA env var per deployment).
