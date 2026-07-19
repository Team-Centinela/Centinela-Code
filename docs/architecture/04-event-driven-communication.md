# Event-Driven Communication

Modules and services communicate asynchronously by publishing and consuming **Domain Events** through **Azure Service Bus**.

## What This Solves

Direct inter-module calls create tight coupling: if the Rule Engine is down, Transaction ingestion blocks. Synchronous calls also make it impossible to extract modules into separate services later. Event-driven communication decouples producers from consumers — each module operates independently and reacts to events at its own pace.

> Broker tier, deduplication, and outbox semantics are codified in `decision-log/ADR-003-async-messaging-reliability.md`.

## Event Flow

```
Ingestion API          Serverless Engine (Rule Engine)   Core Backend (Monolith)
      │                          │                              │
      │  TransactionReceived     │                              │
      ├─────────────────────────▶│                              │
      │                          │                              │
      │                  EVALUATE PIPELINE                      │
      │                  (FR-1 → FR-4 → FR-3 → FR-2)            │
      │                          │                              │
      │                          │ FraudEvaluationCompleted     │
      │                          ├─────────────────────────────▶│  Case Module
      │                          │                              │       │
      │                          │                              │       ▼
      │                          │                              │  Alert Module
      │                          │                              │       │
      │                          │                              │       │ FraudAlertRaised
      │                          │                              │       ▼
      │                          │                              │   Case Module
      │                          │                              │
      │                          │  all hops via Azure Service Bus│
```

## Domain Events Catalog

| Event | Producer | Consumers | What It Triggers |
|-------|----------|-----------|------------------|
| `TransactionReceived` | Ingestion API | Serverless Engine (Rule Engine) | Starts fraud evaluation |
| `FraudEvaluationCompleted` | Serverless Engine (Rule Engine) | Case Module, Alert Module | Creates case if score > threshold, sends alert |
| `FraudAlertRaised` | Alert Module | Case Module, Reporting | Links alert to case, updates dashboard |
| `CaseAssigned` | Case Module | Reporting | Analyst assignment recorded for reports |
| `CaseResolved` | Case Module | Reporting, Alert | Updates case status, closes related alerts |

## Reliable Delivery with Outbox Pattern

To guarantee no event is lost when the message broker is unavailable:

1. Business operation and event are persisted in the same database ACID transaction
2. A background scheduler polls unsent events from the `outbox_events` table
3. Each event is published to Azure Service Bus
4. On success, the event is marked as sent; on failure, it is retried

```
Business Operation (ACID transaction)
├── Save to main table (e.g., transactions)
└── Insert event into outbox_events table
         │
    Outbox Publisher (background, every 1s)
         │
         ├── Success → mark as SENT
         └── Failure → increment retry_count, retry later
```

## Message Contract

Every event shares a standard envelope:

```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "eventType": "FraudEvaluationCompleted",
  "source": "RuleEngine",
  "timestamp": "2026-07-15T12:00:00Z",
  "correlationId": "tx-123456",
  "data": {
    "transactionId": "tx-123456",
    "score": 78.5,
    "triggeredRules": ["FR-1", "FR-3"]
  }
}
```
