# CQRS — Command Query Responsibility Segregation

Separating write operations (commands) from read operations (queries) to optimize each side independently.

## What This Solves

In fraud detection, writes and reads have fundamentally different requirements:
- **Writes** (ingest transaction, evaluate rules, create case) need ACID consistency and domain behavior
- **Reads** (dashboard, reports, case search) need fast denormalized projections and can tolerate slight staleness

CQRS lets us optimize each path without compromise.

## Lightweight CQRS in Centinela

We use a **same-database, same-service** CQRS approach — no separate read models or databases. Commands and queries use different handlers and different models within the same service. The diagram below focuses on the Core Backend, which owns analyst-facing reads (dashboards, case lists, case detail) and the Case / Alert / Reporting write paths. The Ingestion API and the Serverless Engine apply the same pattern in their own scope (write to `oltp.transactions` + outbox; write to `transactions.triggered_rules` + outbox respectively) but never expose analyst-facing read APIs.

```
┌───────────────────────────────────────────────┐
│                 Core Backend                    │
│  ┌─────────────────────────────────────────┐   │
│  │          Command Side (Write)            │   │
│  │  Case → Alert → Audit → Reporting cache  │   │
│  │  Uses: Domain model (rich behavior)      │   │
│  │  Consistency: Strong (ACID)              │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │           Query Side (Read)              │   │
│  │  Dashboard → Reports → Case Search      │   │
│  │  Uses: DTO projections (flat data)      │   │
│  │  Consistency: Eventual (via SELECT-only  │   │
│  │  role `reporting_reader` against the     │   │
│  │  primary — see ADR-002 §Reporting)       │   │
│  └─────────────────────────────────────────┘   │
└───────────────────────────────────────────────┘
```

## Implementation

### Command Handler — Write

```java
@Component
public class OpenCaseHandler {
    private final CaseRepository repository;
    private final EventPublisher eventPublisher;

    @Transactional
    public void handle(OpenCase command) {
        FraudCase fraudCase = FraudCase.open(
            new CaseId(command.caseId()),
            new TransactionId(command.transactionId()),
            FraudScore.from(command.score()),
            command.triggeredRules()
        );
        repository.save(fraudCase);
        eventPublisher.publish(new CaseOpened(fraudCase.id(), command.score()));
    }
}
```

`OpenCaseHandler` is triggered on the Core Backend by the `FraudEvaluationCompleted` event published by the Serverless Engine. It opens a fraud case in `cases.fraudCases` and emits a `CaseOpened` event through the same outbox-protected publisher used everywhere.

### Query Handler — Read

```java
@Component
public class DashboardQueryHandler {
    private final DashboardRepository dashboardRepo;

    public DashboardResponse handle(DashboardQuery query) {
        return new DashboardResponse(
            dashboardRepo.totalTransactionsToday(),
            dashboardRepo.flaggedTransactions(),
            dashboardRepo.openCases()
        );
    }
}
```

`DashboardQueryHandler` connects to PostgreSQL with the `reporting_reader` role (no DDL/DML grants) per `ADR-002 §Reporting`. It returns DTO projections optimised for the analyst dashboard; no domain-aggregate footprint leaks across the boundary.

## When to Use Each Side

| Concern | Command Side | Query Side |
|---------|-------------|-----------|
| **Consistency model** | Strong (ACID transaction) | Eventual (read can land on read-replica in V2) |
| **Data model** | Domain model (Aggregates, Value Objects) | DTO projection (flat, denormalized) |
| **Optimization target** | Write throughput, data integrity | Read performance, response time |
| **Example use case** | Open case from `FraudEvaluationCompleted`, dispatch alert on threshold | Load analyst dashboard, search cases, export reporting |
