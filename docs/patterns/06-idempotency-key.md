# Idempotency Key Pattern

Use an **Idempotency Key** on every async command and event so retried or out-of-order deliveries produce the same outcome as a single delivery.

## What This Solves

Azure Service Bus guarantees **at-least-once** delivery, not exactly-once. Without idempotency:

- Consumer processes the same message twice → double score, double case creation, double alert.
- Outbox publisher retries after a transient broker error → the same event reaches the consumer twice.

Idempotency keys make the **business handler** logically "exactly-once" without betting on broker behavior.

## Idempotency by Layer

Centinela has three layers where idempotency must be enforced:

| Layer | Idempotency key | Enforcement |
|---|---|---|
| **HTTP API** | `Idempotency-Key` header (RFC 9110) sent by ingestion client | If header missing → reject with `400 BAD_REQUEST`. If present → store `(Idempotency-Key, request_hash, ordered_response)` for 24h and short-circuit. See ADR-006. |
| **Internal commands** (saga steps) | Aggregate ID (`transactionId`, `caseId`, `documentId`) | Handler checks `aggregate_id` state before processing; ACK+skip if already in terminal status. |
| **Domain events** (Outbound) | `eventId` (UUID generated on emission) | Service Bus does de-duplication on Standard tier (`enableDuplicateDetection=true`, `messageId=eventId` over a sliding window). |

## Two Consumer-Side Strategies (ADR-003 §3.3)

ADR-003 §3.3 prescribes **two** strategies and lets each consumer pick the one that matches its ownership of the target business table. The two strategies are intentionally equivalent in guarantees; they differ in the SQL primitives they use and in which scenarios they shine.

### §3.3.1 — `processed_events` Ledger (used by Core Backend)

Best when the consumer is **only** writing to a small dedup ledger and does not concurrently mutate a business table. Append-only `INSERT ... ON CONFLICT DO NOTHING` against `(consumer, idempotency_key)`.

```sql
CREATE TABLE processed_events (
    consumer        VARCHAR(100) NOT NULL,        -- 'core-backend', 'serverless-engine', 'ocr-worker', etc.
    idempotency_key VARCHAR(255) NOT NULL,      -- transactionId / caseId / documentId / eventId
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, idempotency_key)
);

INSERT INTO processed_events (consumer, idempotency_key, processed_at)
VALUES ('core-backend', :idempotencyKey, NOW())
ON CONFLICT (consumer, idempotency_key) DO NOTHING;
```

If the INSERT succeeds (row count 1), the message is processed. If it returns 0 (duplicate exists), the handler ACKs and skips. Single atomic statement — no TOCTOU race.

### §3.3.2 — Business Status Gating over `received_messages` (used by Serverless Engine)

Best when the consumer **also** advances business state in the same transaction (case status, alert state, fraud score, audit rows). The ledger row is created on **first** sight of `(consumer, messageId)`, then progressed through `RECEIVED → PROCESSED` over the lifetime of the message. `SELECT ... FOR UPDATE SKIP LOCKED` (PostgreSQL 11+) lets multiple consumer replicas race safely.

The Serverless Engine chose this strategy because its message handler must, in **one** ACID transaction:

1. Run `FraudPipeline.execute(ctx)` and accumulate triggered rules.
2. Persist the `triggered_rules` JSONB audit rows.
3. Append a `FraudEvaluationCompleted` event to the outbox (per ADR-003 §3.2).
4. Advance the dedup row to `PROCESSED` so future redeliveries are deduped.

Wrapping all four in one transaction with the dedup gate as part of the same lock acquisition is exactly what §3.3.2 was designed for.

The sequence inside one ACID transaction:

```java
// 1. SELECT ... FOR UPDATE SKIP LOCKED (PostgreSQL) / LIMIT 1 (H2 test runtime)
List<Row> rows = repo.lockForUpdateSkipping(schema, messageId, consumer);
if (!rows.isEmpty()) {
    Row existing = rows.get(0);
    return switch (existing.status()) {
        case "PROCESSED" -> DUPLICATE_DONE;   // ACK; never re-process
        case "RECEIVED"  -> INFLIGHT_OTHER;   // another replica owns the row
        default          -> INFLIGHT_OTHER;
    };
}

// 2. INSERT ... ON CONFLICT DO NOTHING (Postgres) / MERGE INTO ... KEY (H2)
Row inserted = repo.insertIfAbsent(schema, messageId, consumer, txId);
return inserted != null ? CLAIMED : RACE_LOST;
```

Outcomes:

| Outcome       | Action |
|---|---|
| `CLAIMED`        | Run business work in the same ACID transaction; call `markProcessed(claimedRow)` on success so the row advances to `PROCESSED` for any future redelivery. |
| `DUPLICATE_DONE` | ACK, skip. Another worker has already finished this message and persisted `PROCESSED`. |
| `INFLIGHT_OTHER` | ACK, skip. Another replica holds the `SELECT FOR UPDATE` lock; it will eventually `markProcessed`. |
| `RACE_LOST`      | ACK, skip. The INSERT lost the `ON CONFLICT` race. |

**Why §3.3.2 and not §3.3.1 for the Serverless Engine:** the engine mints a domain decision and writes audit rows. Combining the dedup gate with the business state via `SELECT FOR UPDATE SKIP LOCKED` keeps the whole flow inside a single transaction and gives the engine the same horizontal-scale-without-double-processing guarantee that ADR-003 §3.4 prescribes for the Outbox Pattern.

## Why §3.3.1 is still right for Ingestion and Core Backend

Ingestion and Core Backend consume `transactions-raw` and `case-events` respectively but do not advance business state from the message itself — they only emit derived events downstream. For them, the `processed_events` ledger is sufficient and the simpler `INSERT ON CONFLICT` is enough. The Serverless Engine is the only consumer in Sprint 1 that mints a domain decision and writes audit rows, which is what forces §3.3.2.

## Why not just rely on Service Bus deduplication?

Service Bus de-duplication is a **broker feature**, not a business invariant. It catches duplicate publishes inside a sliding window. It does not protect:

- Duplicate deliveries from the Outbox scheduler (if the same event is selected twice due to partition locking issues in PostgreSQL < 11 or unusual HA fail-over scenarios).
- Manual replay from dead-letter queues.
- Cross-regional or cross-topic redelivery paths added later.

A second layer of consumer-side idempotency is mandatory.

## Idempotency in Edge Cases

| Edge case | Handling |
|---|---|
| Outbox publishes twice (rare, possible during HA fail-over) | Service Bus dedupe catches it; consumer-side ledger is extra defense |
| Service Bus redelivery after consumer takes > lock timeout | §3.3.2 consumer re-enters the SELECT FOR UPDATE gate; §3.3.1 consumer re-runs the INSERT ON CONFLICT gate. Both safely skip if the previous attempt committed. |
| Operator manually replays from DLQ | Consumer-side check provides safety |
| Saga trigger event arrives twice | The idempotency record blocks the second execution |

## When to pick which strategy

| Pick §3.3.1 when... | Pick §3.3.2 when... |
|---|---|
| The consumer's only DB write per message is the dedup ledger itself. | The consumer also advances business state (case status, fraud decision, audit rows). |
| The consumer does not need to mutate a business table in the same message. | Multiple consumer replicas may scale horizontally (ADR-009 §9.1 KEDA). |
| Simpler reasoning; no `SKIP LOCKED`-specific database feature requirement; runs unchanged on any relational engine. | A single ACID transaction must span the dedup gate, the business write, and the outbox append. |

If a consumer grows from "ledger-only" into "also advances business state", its idempotency strategy must migrate from §3.3.1 to §3.3.2 in a coordinated PR; do not mix the two ledgers for the same consumer.

## Cross-Doc Effect

- `../decision-log/ADR-003-async-messaging-reliability.md` §3.3 — pins both strategies.
- `docs/architecture/04-event-driven-communication.md` — enumerates Service Bus entity + consumer-pair so a §3.3.2 migration is documented alongside the message flow.
- ADR-007 §7.6 — W3C `traceId` stamped onto the dedup ledger row for cross-saga queries.

## References

- `03-outbox-pattern.md` — Outbox Pattern (uses idempotency implicitly)
- `../architecture/04-event-driven-communication.md` — domain event envelope
- `../architecture/06-technology-stack.md` — Service Bus Standard tier (requires for `enableDuplicateDetection`)
- `../decision-log/ADR-003-async-messaging-reliability.md` §3.3 — both strategies
- ADR-006 (Security & Auth) — HTTP `Idempotency-Key` header on Ingestion

## Status

**LIVE — both strategies shipped.**

- §3.3.1 (`processed_events` ledger) is implemented via `IdempotencyService` + `processed_events` table in Core Backend. Used by the Core Backend's `case-events` consumer.
- §3.3.2 (`received_messages` ledger) is implemented via `ReceivedMessageIdempotencyService` + `oltp.received_messages` table in the Serverless Engine. Used by `TransactionsRawConsumer` for the `transactions-raw` queue.

PR #130 was the design + first integration pass for the engine's half of epic #54.

## Related Code

### §3.3.1 — Core Backend

- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/IdempotencyService.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/ProcessedEventEntity.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/ProcessedEventJpaRepository.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/adapter/messaging/TransactionsRawConsumer.java`

### §3.3.2 — Serverless Engine

- `services/serverless-engine/src/main/java/com/centinela/serverless/infrastructure/idempotency/ReceivedMessageIdempotencyService.java`
- `services/serverless-engine/src/main/java/com/centinela/serverless/infrastructure/idempotency/ReceivedMessageRepository.java`
- `services/serverless-engine/src/main/java/com/centinela/serverless/infrastructure/idempotency/IdempotencyDialectResolver.java` (PostgreSQL vs H2 dialect switch for `SELECT FOR UPDATE SKIP LOCKED`)
- `services/serverless-engine/src/main/java/com/centinela/serverless/adapter/in/consumer/TransactionsRawConsumer.java`
- `services/serverless-engine/src/main/resources/db/migration/common/V1__init_engine_schemas.sql` (creates the `received_messages` ledger plus its `message_id, consumer, status, received_at, processed_at, transaction_id` columns)
