# ADR-003: Async Messaging & Reliability

## Context

Cross-module coordination in the modular monolith must be async by rule (`../architecture/02-modular-monolith.md` § Communication Rules). The async layer must:

1. Guarantee at-least-once delivery for every domain event (no silent drops).
2. Survive transient broker outages without needing distributed transactions.
3. Expose idempotency on the consumer side as a first-class concern.
4. Stay under the $60 budget over the 21-day project.
5. Provide publish/subscribe semantics **and** queue semantics; the architecture references both a `case-events` **topic** (subscriber model) and `documents-pending` / `transactions-raw` **queues** (single-consumer model).

Three small decisions are bundled under this ADR: message broker choice, transport primitives (queues vs topics), and reliability patterns.

## Decision

### 3.1 Message broker: Azure Service Bus (Standard tier)

| Property | Decision |
|---|---|
| Broker | Azure Service Bus |
| **Tier** | **Standard** |
| **Why Standard, not Basic** | Basic tier forbids Topics. The architecture's `case-events` topic (used for analytics, alerting, future notification consumers) requires Topics. Standard also enables Scheduled Messages, Sessions, Transactions, ForwardTo (forwarding), Large Messages up to 100 KB (Standard) vs 256 KB (Basic), and De-duplication. |
| Cost | Base charge ≈ $10/mo (first 13M ops/month free, then tiered). For 21 days ≈ **~$7** |
| Subscriptions | `shared` topic `case-events` with two subscriptions: `reporting-updates` and `alerts` |
| Queues | `transactions-raw`, `documents-pending` |

**Client library pinning** (per #26 — Week 1 decision, not Week 2):

| Service | Runtime | Client | Version source |
|---|---|---|---|
| Core Backend | Java 21 / Spring Boot 3.4.x | `com.azure.spring:spring-cloud-azure-starter-servicebus` (Spring Cloud Azure Service Bus binder) over `spring-cloud-stream` 4.x | Pinned in `services/pom.xml` (`<spring-cloud-azure.version>5.19.0</spring-cloud-azure.version>`, `azure-messaging-servicebus 7.17.7`); see PR #36 for the foundation and sub-issue #39 for the explicit `spring-cloud-stream` pin still pending. |
| Ingestion API | Java 21 / Spring Boot 3.4.x | Same as Core Backend — shares `services/pom.xml` parent. Same version source. | |
| Serverless Engine (Rule Engine) | Java 21 / Spring Boot 3.4.x | Same binder chain as Core Backend and Ingestion API — its single consumer adapter binds to the `transactions-raw` queue subscription. KEDA `azure-servicebus` scaler reads from the same `Manage`-policy connection. | Pinned in `services/pom.xml`. |
| OCR Worker | Python 3.12 / FastAPI | `azure-servicebus` 7.x async client (PyPI `azure-servicebus`) | Pinned in `services/ocr-worker/pyproject.toml` once issue #40 implementation lands. |

Why the binder (not raw SDK): the Spring Cloud Azure Service Bus binder is queue/topic-aware, integrates with `spring-cloud-stream` declarative bindings, and gives us idempotency primitives (NACK vs ACK) aligned with ADR-003 §3.3. Raw `com.azure:azure-messaging-servicebus` is reserved for places where the binder model is too restrictive (e.g. session-keyed-by-`aggregateId` for in-order per-aggregate processing). Versions must appear in build files on **Day 1** and may not be bumped during Sprint 1 without an ADR amendment issue.

**Tunnel**: Service Bus Standard is required by the architecture, not Basic. The `../architecture/06-technology-stack.md` and `../architecture/04-event-driven-communication.md` files adopt this tier; any cost projection cited from earlier drafts that mentioned "Basic" must be corrected — the cost line increases by ~$7 over the project window, well under budget.

### 3.2 Outbox Pattern is mandatory for every service that publishes a domain event

To avoid the dual-write problem across multiple modules, every module publishing a domain event uses the Outbox Pattern documented in `../patterns/03-outbox-pattern.md`:

```
Business Table Write + Outbox Insert (same ACID transaction)
       │
       ▼
Outbox Publisher (scheduled @1s in every deploying service — Ingestion API, Serverless Engine, and Core Backend)
       │
       ▼
Service Bus (queue or topic)
```

**No shortcuts allowed**, including in the Ingestion Service (which is an independent deployment). The Ingestion Service will:
- Persist `transaction` rows to PostgreSQL `oltp` schema.
- Insert into `outbox_events` in the same transaction.
- Run its own Outbox publisher `scheduled` task that drains and publishes to the `transactions-raw` queue.

This is non-negotiable for ADRs compressing two writes (DB + broker) into one durable operation. Outbox is the published-through classifier for cross-module reliability.

**Outbox row lifecycle** (per #25 — closes the shutdown / cold-start gap):

```
                              ┌──────────────────────┐
INSERT (status=PENDING,        │  Scheduled tick @1s  │
  attempts=0,                  │  SELECT ... FOR      │
  last_attempt_at=NULL)        │   UPDATE SKIP        │
 ─────────────────────────────►│   LOCKED             │
                                │   WHERE status=      │
                                │   'PENDING'          │
                                │   ORDER BY id        │
                                │   LIMIT 100          │
                                └──────────┬───────────┘
                                           │
                 ┌─────────────────────────┼─────────────────────────┐
                 ▼                         ▼                         ▼
   publish OK                   broker transient 5xx          JVM shutdown /
   ───────────────              ──────────────────            unclean crash
   UPDATE status=PUBLISHED,     attempts++,                    ──────────────
   published_at=NOW()           backoff (exp, max 10/60s)     row stays
                                                               status=PENDING,
   After 10 attempts                                           attempts>0
   ────────────────
   UPDATE status=DEAD_LETTER,
   alert fired
```

**Recovery / cold-start semantics** (addresses scenarios flagged in #25):

1. **Graceful shutdown.** The Outbox Publisher bean implements `@PreDestroy` with a configurable drain timeout (default 10 s). On stop, no new rows are claimed and in-flight `SELECT ... FOR UPDATE SKIP LOCKED` transactions are committed before the JVM exits. Spring `web.server.graceful-shutdown` and `spring.lifecycle.timeout-per-shutdown-phase` cover the HTTP boundary; the publisher implements its own drain so the two lifecycles do not race.
2. **Unclean crash / cold start.** On application-ready (`@PostConstruct` phase), the publisher blocks on a JDBC `SELECT 1` ping against PostgreSQL with a 5 s budget. The same connection pool then runs a 60 s periodic **recovery step** that resets any `status=PENDING` row older than 5 minutes back to `attempts=0, last_attempt_at=NULL` — idempotent because `SKIP LOCKED` already prevents double-publish.
3. **PostgreSQL B1ms auto-stop race.** The startup ping above is the guard. If the ping fails after 5 s, the publisher self-defers (a `PublisherStatus.DEGRADED` state) and Application Insights receives a `outbox-publisher-degraded` event. Listener backoff on the readiness probe keeps the container from receiving traffic until the DB is reachable.
4. **Observable lag.** `/actuator/health/outbox-lag` exposes two Micrometer gauges — `outbox.pending.count` and `outbox.pending.oldest.seconds` — wired into Application Insights. SLO: after broker recovery, no more than **120 s** of event backlog.

The recovery step does not require a schema change beyond `last_attempt_at TIMESTAMPTZ NULL` and `attempts INT NOT NULL DEFAULT 0`, both already implied by ADR-003 §3.4's idempotency table. Concrete DDL lives in the service-local Flyway migrations: `services/core-backend/src/main/resources/db/migration/V2__outbox_schema.sql` and `services/ingestion/src/main/resources/db/migration/V1__init_schemas_and_tables.sql` (added in #40).

### 3.3 Idempotency Key strategy

Every domain event carries an `aggregateId` (e.g. `transactionId`) used as the Idempotency Key. Two strategies are used, depending on the consumer's ownership of the target table:

#### 3.3.1 Inbound message dedup — `processed_events` ledger

Consumers that receive events from a queue or topic subscription dedup via a `processed_events` ledger table with an atomic `INSERT ... ON CONFLICT DO NOTHING`. This is the primary mechanism for event-driven consumers:

```sql
INSERT INTO processed_events (consumer, idempotency_key, processed_at)
VALUES (:consumer, :aggregateId, NOW())
ON CONFLICT (consumer, idempotency_key) DO NOTHING;
```

Returns 1 if inserted (first-time processing), 0 if duplicate. The `IdempotencyService` wraps this as a `@Transactional` method. On duplicate, the handler ACKs and skips. On INSERT failure, the exception aborts the transaction and the message is redelivered.

This is the correct choice here because the consumer **is** the writer of the `processed_events` row — there is no canonical business row to gate on at the moment of ingestion.

#### 3.3.2 Business status gating — `SELECT ... FOR UPDATE SKIP LOCKED`

For consumers that mutate an existing business table (e.g. case creation or alert advancement), the **SELECT-then-conditional-UPDATE** strategy is used:

```
SELECT status
  FROM <table>
 WHERE aggregate_id = ?
   AND status >= <expected_status>
   FOR UPDATE SKIP LOCKED
```

If the row exists in the expected status, **skip** processing and ACK the message. Otherwise UPDATE with an idempotent guard (`WHERE status < expected_status`). This strategy is reserved for business-table writers where the consumer advances application state.

**Why not the alternatives** flagged in #16:

- *Sessions-keyed-by-aggregateId* — sessions add session-create-then-renew cost and break the binder's at-least-once retry contract when a consumer crashes mid-session. Rejected.
- *INSERT ON CONFLICT on business tables* — requires the consumer to be the writer of the canonical row, which it is not (`cases`/`alerts` are mutated under explicit use-case code). Rejected for business tables; used for the `processed_events` ledger (§3.3.1).
- *Plain SELECT (no FOR UPDATE)* — leaves a TOCTOU window for at-least-once duplicates. Rejected in favour of the locked read.

Implementation of both strategies lands in #41 (consumer-side idempotency, sub-issue of #37). The `processed_events` ledger and `IdempotencyService` live in each service's `shared/idempotency/` package.

### 3.4 Failure handling escalator

| Failure | Reaction |
|---|---|
| Service Bus transient (5xx, throttling) | Exponential backoff retry inside the publisher, max 10 attempts in 60s |
| Persistent broker failure (>10 attempts) | Events marked `DEAD_LETTER`; admin endpoint to inspect & replay |
| Consumer processing crash mid-message | Service Bus lock timeout → redelivery to next consumer instance |
| Consumer poison message (deterministic crash on aggregateId) | After 3 deliveries to consumer (`max_delivery_count = 3`), Service Bus auto-routes to `<entity>-poison` queue / `<subscription>-poison` topic-subscription. Application Insights alert on the first message routed to any `-poison` endpoint. |
| Outbox row advancement race | Use `SELECT ... FOR UPDATE SKIP LOCKED` (PostgreSQL 11+) to allow horizontal scaling without double-publishing |
| JVM shutdown / unclean crash mid-publish | `@PreDestroy` drain timeout (default 10 s) + periodic recovery step resets stale `status=PENDING` rows older than 5 min (see §3.2) |
| Cold-start before DB is reachable | `@PostConstruct` JDBC ping with 5 s budget; readiness probe gates traffic; Application Insights receives `outbox-publisher-degraded` if it fails |

**IaC pinning** (per #27 — addresses `maxDeliveryCount` overriding):

The ADR's `max_delivery_count = 3` value is governed by Terraform, not application config:

- Location: `infrastructure/modules/servicebus/main.tf` (provisioning) plus per-entity `.tf` files: `queues-transactions-raw.tf`, `queues-documents-pending.tf`, `topics-case-events.tf`.
- All three queues (`transactions-raw`, `documents-pending`) and both topic subscriptions (`case-events/reporting-updates`, `case-events/alerts`) set `max_delivery_count = 3`.
- Poison routing is provisioned as `azurerm_servicebus_subscription_rule` forwarding-on-dead-letter (or the equivalent for queues) to the named `-poison` sibling. Cost: 5 additional entities (~0 at Standard tier; well under the $10/mo base charge).
- Drift detection via `terraform plan` in CI; a `checkov` or `tflint` policy encodes the invariant "no Service Bus queue/subscription ships without a `-poison` sibling".

The ADR text **does not** carry `max_delivery_count = 3` literal into Java/Python config — application code only reads the property at runtime. This keeps the value immutable under ad-hoc deploys.

## Consequences

### Positive
- Outbox Pattern guarantees no observed event loss even if Service Bus is down for the entire project window.
- Standard tier unlocks topic/subscription/forwarding for future modules without re-architecting.
- Idempotency key strategy works across every hop (Ingestion → Serverless Engine, Serverless Engine → Core Backend, Core Backend → OCR Worker).
- One pattern (`outbox_events`) covers both in-monolith and out-of-monolith payloads.

### Negative
- Standard tier is ~$7 more than Basic for the 21-day window (still $35+ under the $60 ceiling).
- Outbox table adds +1 column-per-event to the database schema overhead; trivial for our scale.
- `SELECT ... FOR UPDATE SKIP LOCKED` requires PostgreSQL ≥ 11 (our B1ms instance ships with PostgreSQL 16 by default — fine).

## Alternatives considered

| Alternative | Reason rejected |
|---|---|
| Azure Storage Queues | No topics; no scheduled; no native transactions; no dead-letter; weaker delivery semantics. |
| Azure Event Grid | Pub/sub only — no queue primitive; no scheduled messages; no de-dup. |
| RabbitMQ on a Container App | Operational complexity (patch, scale, monitor) for a 4-person / 21-day project. |
| Service Bus **Basic** tier | No Topics → breaks `case-events`. Selecting Basic before mapping the architecture to the tier was an error in earlier drafts. |
| Postgres LISTEN/NOTIFY (no broker) | NOT for cross-process events; ordering, replay, dead-letter, fan-out all weak. |

## References

- `../architecture/04-event-driven-communication.md` — domain event catalog
- `../patterns/03-outbox-pattern.md` — full Outbox Pattern implementation
- Historical GitHub Issue [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4) (existing ADR draft on messaging) — superseded
- [#12](https://github.com/Team-Centinela/Centinela-Code/issues/12) ADR-003 issue tracker — Supersedes [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4)
- `ASSIGNMENT.md` §T.1, §T.3 — inter-component contract
- `ASSIGNMENT.md` §E — access patterns influencing this decision
- `infrastructure/README.md` — Azure resource budget and IaC ownership (Day-1 quota link)
- Sub-issues that close the implementation side of this ADR: #39 (`spring-cloud-stream` pin in `services/pom.xml`), #40 (Outbox Publisher with shutdown recovery + cold-start handling). Both roll up to epic #37.

## Status

**ACCEPTED** — Ratified during Sprint 0 ADR review (#16) after blockers #22, #25, #26, and #27 were resolved. Sub-issues #39 (spring-cloud-stream pin) and #40 (Outbox Publisher implementation) remain on epic #37 / `feature/architecture` branch track and will close when implementation lands.
