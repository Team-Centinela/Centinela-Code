# Outbox Pattern

Guarantees reliable event delivery to Azure Service Bus without coupling database transactions to broker transactions.

> This pattern is mandated for every service that publishes a domain event: the Ingestion API, the Serverless Engine, and the Core Backend each run their own Outbox Publisher. Authority: `../decision-log/ADR-003-async-messaging-reliability.md`. Companion: `../patterns/06-idempotency-key.md` for consumer-side dedup.

## The Problem

When saving a transaction and publishing an event, two things can go wrong:
1. The database save succeeds but Service Bus is unavailable — the event is lost
2. Service Bus receives the event but the database save fails — phantom event with no data

Distributed transactions (XA) are slow, complex, and often unsupported by cloud brokers.

## The Solution

Write the event to the same database, in the same ACID transaction, as the business data. A background process reads unsent events and publishes them to the broker. Every service that owns event-emitting aggregates runs its own publisher (the Ingestion API publishes `TransactionReceived` to the `transactions-raw` queue; the Serverless Engine publishes `FraudEvaluationCompleted` to the `case-events` topic; the Core Backend publishes `CaseOpened` / `FraudAlertRaised` / `CaseResolved`).

```
Business Operation (ACID)
├── transaction: INSERT INTO transactions ...
└── outbox: INSERT INTO outbox_events (event_type, payload, status='PENDING')

Outbox Publisher (@Scheduled, every 1 second — one in each deploying service)
├── SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at
├── For each event:
│   ├── Publish to Azure Service Bus
│   ├── Success → UPDATE status = 'PUBLISHED', published_at = NOW()
│   └── Failure → UPDATE retry_count = retry_count + 1
└── (Events with retry_count > 10 are marked DEAD_LETTER)
```

## Database Table

```sql
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP,
    retry_count    INT DEFAULT 0,
    status         VARCHAR(20) DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_pending
    ON outbox_events (status, created_at)
    WHERE status = 'PENDING';
```

## Java Implementation

```java
@Component
public class OutboxPublisher {
    private final OutboxEventRepository outboxRepo;
    private final ServiceBusClient serviceBusClient;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepo.findAllPending();
        for (OutboxEvent event : pending) {
            try {
                ServiceBusSender sender = serviceBusClient.createSender(event.getEventType());
                ServiceBusMessage message = new ServiceBusMessage(event.getPayload());
                // Always-set contract (Phase 0.2.6 / §30.5 S2 #2): the Service
                // Bus messageId MUST equal outbox_events.id, the deterministic
                // rail ADR-003 §3.3.1's processed_events / received_messages
                // ledger relies on. Consumer TransactionsRawConsumer throws
                // when the header is absent, so a missing id here would cause
                // legitimate redeliveries to be dead-lettered.
                message.setMessageId(event.getId().toString());
                sender.sendMessage(message);
                outboxRepo.markSent(event.getId());
            } catch (Exception e) {
                outboxRepo.incrementRetry(event.getId());
            }
        }
    }
}
```

> The reference implementation in `services/shared-messaging/` uses the
> Spring Cloud Stream binder (`StreamBridge`) rather than the raw
> `azure-messaging-servicebus` client — the always-set contract applies
> identically: the `messageId` header MUST be set on every outbound
> `MessageBuilder.withPayload(payload).setHeader("messageId", outboxRowId)`
> call, and the binder propagates it to the native SB message-id property.
> See `ServiceBusPublisherImpl.java` and `OutboxPublisher.java`.

## Recovery

- Events exceeding max retries (configurable, default 10) move to `DEAD_LETTER` status
- A manual admin endpoint allows inspecting and replaying dead-lettered events
- Monitoring alerts when any event has `retry_count > 5`
- Cleanup job removes `SENT` events older than 7 days

## Behavior under planned PostgreSQL downtime (B1ms auto-stop)

`ADR-002` (§ Planned DB downtime & outbox restart-drain) puts the B1ms Flexible Server into a stopped state during non-testing hours (~12 h/day) to honor `ASSIGNMENT.md` §3 *"Resource cleanup/shutdown when not actively testing"*. The Outbox Pattern is **compatible** with planned PostgreSQL restarts — no special handling is required at the application layer.

| Phase | What happens to writers | What happens to events | What happens to readers |
|---|---|---|---|
| **Stop issued** (Azure Automation runbook) | Ingestion API returns `503` with `Retry-After`. Serverless Engine and Core Backend keep their JVMs warm but pending consumer messages sit on Service Bus. | `outbox_events` rows inserted in the last transaction remain in `status='PENDING'`. No new rows can be inserted because the writer's `BEGIN` fails on connection refused. | Outbox Publisher's next poll hits a connection error, logs it at `WARN`, retries on the next tick. |
| **Stopped window (≤12 h)** | All HTTP ingress returns `503`. | Events from before stop remain on durable storage in `status='PENDING'`. Service Bus queues keep new messages inside Service Bus (broker-side, not PostgreSQL). | None. |
| **Start issued** (runbook) | Cold-start overhead: ~60–120 s for `READY` state on B1ms. | Rows unchanged. | Publisher reconnects; the existing `SELECT … ORDER BY created_at` query finds every `PENDING` row from before the stop and publishes them. |
| **First poll after start** | Writers reconnect (Container App warm-up; no cold-start for Spring Boot replicas unless scaled to zero). | The backlog drains in seconds at `@Scheduled(fixedDelay = 1000)` cadence. | Consumers see the burst through Service Bus; consumer-side idempotency (`06-idempotency-key.md`) prevents duplicate work. |

**Why this is safe:**

1. The outbox writer is **the same transaction** as the business data write (`INSERT INTO transactions … ; INSERT INTO outbox_events …`). If the writer can reach PostgreSQL, the event is durable; if it can't, the writer's transaction never starts, so the event is also never observed by anyone except the client. There is no in-flight state that bridges a stopped database to a running database without a completed transaction.
2. PostgreSQL Flexible Server's stop state preserves the data volumes; `outbox_events` rows are not lost across a restart.
3. The Outbox Publisher does not need to track "which events existed before the stop" — the `SELECT … WHERE status='PENDING'` query enumerates the entire backlog atomically on every poll.
4. Consumer-side idempotency (mandated by `ADR-003` §3.3 and detailed in `06-idempotency-key.md`) makes the post-restart burst a non-event from the consumers' perspective; the same `transactionId` arriving twice does not produce two scores or two cases.

**Operational carve-outs (encoded in the auto-stop schedule, see #10):**

| Carve-out | Reason |
|---|---|
| Auto-stop is **skipped** during the active test windows of Sprints 1, 2, 3 (declared in `ASSIGNMENT.md` §Timeline). | The team is actively testing during these hours. |
| Auto-stop is **skipped** during the 2 h before and after a manual start command. | Gives the team time to land smoke tests before the next stop kicks in. |
| Auto-stop is **disabled** while a `case-events` backfill or a one-shot data migration is in progress (signaled via a flag; see `#10`). | Backfills prefer reduced DB jitter. |

These carve-outs are operational concerns, not pattern concerns. The pattern itself is unchanged.
