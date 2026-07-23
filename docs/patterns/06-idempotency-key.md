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

## Recommended Idempotency Record Schema

For each async command or event consumed, persist a small "processed events" ledger:

```sql
CREATE TABLE processed_events (
    consumer        VARCHAR(100) NOT NULL,        -- 'core-backend', 'serverless-engine', 'ocr-worker', etc.
    idempotency_key VARCHAR(255) NOT NULL,      -- transactionId / caseId / documentId / eventId
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, idempotency_key)
);
```

```sql
-- INSERT ON CONFLICT DO NOTHING is the atomic dedup primitive.
-- Returns 1 if inserted (first time), 0 if already existed (duplicate).
INSERT INTO processed_events (consumer, idempotency_key, processed_at)
VALUES ('core-backend', :idempotencyKey, NOW())
ON CONFLICT (consumer, idempotency_key) DO NOTHING;
```

If the INSERT succeeds (row 1), the message is processed. If it returns 0 (duplicate exists), the handler ACKs and skips. This is a single atomic statement — no TOCTOU race.

The `IdempotencyService` wraps this as:

```java
@Transactional
public boolean tryProcess(String consumer, String idempotencyKey) {
    return repository.tryInsert(consumer, idempotencyKey) == 1;
}
```

Failure of the INSERT raises an exception that aborts the `@Transactional`, which causes the Service Bus message NOT to be ACKed → automatic redelivery.

## Why not just rely on Service Bus deduplication?

Service Bus de-duplication is a **broker feature**, not a business invariant. It catches duplicate publishes inside a sliding window. It does not protect:

- Duplicate deliveries from the Outbox scheduler (if the same event is selected twice due to `SELECT FOR UPDATE` partition locking issues in older PostgreSQL).
- Manual replay from dead-letter queues.
- Cross-regional or cross-topic redelivery paths added later.

A second layer of consumer-side idempotency is mandatory.

## Idempotency in `Egde Cases`

| Edge case | Handling |
|---|---|
| Outbox publishes twice (rare, possible during HA fail-over) | Service Bus dedupe catches it; consumer-side `processed_events` is extra defense |
| Service Bus redelivery after consumer takes > lock timeout | Consumer processes the redelivery; `processed_events` skip makes it safe |
| Operator manually replays from DLQ | Consumer-side check provides safety |
| Saga trigger event arrives twice (e.g., from a future fan-out bug) | The idempotency record blocks the second execution |

## References

- `03-outbox-pattern.md` — Outbox Pattern (uses idempotency implicitly)
- `../architecture/04-event-driven-communication.md` — domain event envelope
- `../architecture/06-technology-stack.md` — Service Bus Standard tier (requires for `enableDuplicateDetection`)
- ADR-006 (Security & Auth) — HTTP `Idempotency-Key` header on Ingestion

## Status

**LIVE** — implemented via `IdempotencyService` + `processed_events` table in core-backend. Used by `TransactionsRawConsumer`. Reused by every subsequent consumer (add a `ProcessedEventJpaRepository` + `IdempotencyService` in the consuming service, wire into the handler).

## Related Code

- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/IdempotencyService.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/ProcessedEventEntity.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/shared/idempotency/ProcessedEventJpaRepository.java`
- `services/core-backend/src/main/java/com/centinela/corebackend/adapter/messaging/TransactionsRawConsumer.java`
