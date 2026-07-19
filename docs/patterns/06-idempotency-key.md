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
    consumer       VARCHAR(100) NOT NULL,        -- 'serverless-engine', 'case-module', 'alert-module', 'ocr-worker', etc.
    idempotency_key VARCHAR(255) NOT NULL,      -- transactionId / caseId / documentId / eventId
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, idempotency_key)
);
```

```
Consumer-side handler (pseudocode):

@Transactional
public void onMessage(EventEnvelope env) {
    String id = env.aggregateId();
    if (processedRepo.exists("serverless-engine", id)) {
        return;                                   // already processed; safe to ack
    }
    runScoring(id);
    processedRepo.save("serverless-engine", id);
}
```

Failure of either DB insert raises an exception that aborts the `@Transactional`, which causes the Service Bus message NOT to be ACKed → automatic redelivery.

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

- `patterns/03-outbox-pattern.md` — Outbox Pattern (uses idempotency implicitly)
- `architecture/04-event-driven-communication.md` — domain event envelope
- `architecture/06-technology-stack.md` — Service Bus Standard tier (requires for `enableDuplicateDetection`)
- ADR-006 (Security & Auth) — HTTP `Idempotency-Key` header on Ingestion

## Status

**DRAFT** — first implementation in the Serverless Engine consumer of `transactions-raw`. Pattern is reused thereafter by every other consumer.
