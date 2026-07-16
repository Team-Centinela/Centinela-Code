# ADR-003: Async Messaging & Reliability

## Status
**DRAFT** — Pending team review and approval (supersedes the outbox-pattern language in [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4), now closed).

## Context

Cross-module coordination in the modular monolith must be async by rule (`architecture/02-modular-monolith.md` § Communication Rules). The async layer must:

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

**Tunnel**: Service Bus Standard is required by the architecture, not Basic. The `architecture/06-technology-stack.md` and `architecture/04-event-driven-communication.md` files adopt this tier; any cost projection cited from earlier drafts that mentioned "Basic" must be corrected — the cost line increases by ~$7 over the project window, well under budget.

### 3.2 Outbox Pattern is mandatory for ALL modules

To avoid the dual-write problem across multiple modules, every module publishing a domain event uses the Outbox Pattern documented in `patterns/03-outbox-pattern.md`:

```
Business Table Write + Outbox Insert (same ACID transaction)
       │
       ▼
Outbox Publisher (scheduled @1s in Core Backend process)
       │
       ▼
Service Bus (queue or topic)
```

**No shortcuts allowed**, including in the Ingestion Service (which is an independent deployment). The Ingestion Service will:
- Persist `transaction` rows to PostgreSQL `oltp` schema.
- Insert into `outbox_events` in the same transaction.
- Run its own Outbox publisher `scheduled` task that drains and publishes to the `transactions-raw` queue.

This is non-negotiable for ADRs compressing two writes (DB + broker) into one durable operation. Outbox is the published-through classifier for cross-module reliability.

### 3.3 Idempotency Key strategy

Every domain event carries an `aggregateId` (e.g. `transactionId`) used as the Idempotency Key. The consumer of every queue and topic subscription dedupes by:

```
SELECT 1 FROM <table> WHERE aggregate_id = ? AND status >= <expected_status>
```

If the row exists in the expected status, **skip** processing and ACK the message. This is the same idempotency strategy for both the Ingestion → Scoring and the Core Backend → OCR Worker hops.

### 3.4 Failure handling escalator

| Failure | Reaction |
|---|---|
| Service Bus transient (5xx, throttling) | Exponential backoff retry inside the publisher, max 10 attempts in 60s |
| Persistent broker failure (>10 attempts) | Events marked `DEAD_LETTER`; admin endpoint to inspect & replay |
| Consumer processing crash mid-message | Service Bus lock timeout → redelivery to next consumer instance |
| Consumer poison message (deterministic crash on aggregateId) | After 3 deliveries to consumer (`maxDeliveryCount=3`), Service Bus auto-routes to `<queue>-poison` queue; alerting on first poison |
| Outbox row advancement race | Use `SELECT ... FOR UPDATE SKIP LOCKED` (PostgreSQL 11+) to allow horizontal scaling without double-publishing |

## Consequences

### Positive
- Outbox Pattern guarantees no observed event loss even if Service Bus is down for the entire project window.
- Standard tier unlocks topic/subscription/forwarding for future modules without re-architecting.
- Idempotency key strategy works across both branches (Ingestion → Core, Core → OCR Worker).
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

- `architecture/04-event-driven-communication.md` — domain event catalog
- `patterns/03-outbox-pattern.md` — full Outbox Pattern implementation
- Historical GitHub Issue [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4) (existing ADR draft on messaging) — superseded
- [#12](https://github.com/Team-Centinela/Centinela-Code/issues/12) ADR-003 issue tracker — Supersedes [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4)
- `ASSIGNMENT.md` §T.1, §T.3 — inter-component contract
- `ASSIGNMENT.md` §E — access patterns influencing this decision

## Status

**DRAFT** — pending Sprint 0 review on [`gh issue list --label adr --state open`](https://github.com/Team-Centinela/Centinela-Code/issues?q=is%3Aopen+label%3Aadr). If accepted, [#12](https://github.com/Team-Centinela/Centinela-Code/issues/12) ADR-003 issue moves from *draft* label and the historical [Team-Centinela/Centinela-docs#4](https://github.com/Team-Centinela/Centinela-docs/issues/4) stays closed.
