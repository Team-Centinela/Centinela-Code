# CQRS — Command Query Responsibility Segregation

Separating write operations (commands) from read operations (queries) to optimize each side independently.

## What This Solves

In fraud detection, writes and reads have fundamentally different requirements:
- **Writes** (ingest transaction, evaluate rules, create case) need ACID consistency and domain behavior
- **Reads** (dashboard, reports, case search) need fast denormalized projections and can tolerate slight staleness

CQRS lets us optimize each path without compromise.

## Lightweight CQRS in Centinela

We use a **same-database, same-module** CQRS approach — no separate read models or databases. Commands and queries use different handlers and different models within the same module.

```
┌───────────────────────────────────────────────┐
│                 Core Backend                    │
│  ┌─────────────────────────────────────────┐   │
│  │          Command Side (Write)            │   │
│  │  Transaction → Rules → Evaluation → Case │   │
│  │  Uses: Domain model (rich behavior)      │   │
│  │  Consistency: Strong (ACID)              │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │           Query Side (Read)              │   │
│  │  Dashboard → Reports → Case Search      │   │
│  │  Uses: DTO projections (flat data)      │   │
│  │  Consistency: Eventual (if read-replica) │   │
│  └─────────────────────────────────────────┘   │
└───────────────────────────────────────────────┘
```

## Implementation

### Command Handler — Write

```java
@Component
public class EvaluateTransactionHandler {
    private final TransactionRepository repository;
    private final RuleEngine ruleEngine;
    private final EventPublisher eventPublisher;

    @Transactional
    public void handle(EvaluateTransaction command) {
        Transaction tx = repository.findById(command.transactionId());
        FraudEvaluation evaluation = tx.evaluate(ruleEngine);
        repository.save(tx);
        eventPublisher.publish(new FraudEvaluationCompleted(tx.id(), evaluation));
    }
}
```

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

## When to Use Each Side

| Concern | Command Side | Query Side |
|---------|-------------|-----------|
| **Consistency model** | Strong (ACID transaction) | Eventual (read replica possible) |
| **Data model** | Domain model (Aggregates, Value Objects) | DTO projection (flat, denormalized) |
| **Optimization target** | Write throughput, data integrity | Read performance, response time |
| **Example use case** | Submit transaction, evaluate rules | Load analyst dashboard, search cases |
