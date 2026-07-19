# Outbox Pattern

Guarantees reliable event delivery to Azure Service Bus without coupling database transactions to broker transactions.

> This pattern is mandated for every service that publishes a domain event: the Ingestion API, the Serverless Engine, and the Core Backend each run their own Outbox Publisher. Authority: `decision-log/ADR-003-async-messaging-reliability.md`. Companion: `patterns/06-idempotency-key.md` for consumer-side dedup.

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
│   ├── Success → UPDATE status = 'SENT', sent_at = NOW()
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
    sent_at        TIMESTAMP,
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
                sender.sendMessage(new ServiceBusMessage(event.getPayload()));
                outboxRepo.markSent(event.getId());
            } catch (Exception e) {
                outboxRepo.incrementRetry(event.getId());
            }
        }
    }
}
```

## Recovery

- Events exceeding max retries (configurable, default 10) move to `DEAD_LETTER` status
- A manual admin endpoint allows inspecting and replaying dead-lettered events
- Monitoring alerts when any event has `retry_count > 5`
- Cleanup job removes `SENT` events older than 7 days
