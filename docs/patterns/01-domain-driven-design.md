# Domain-Driven Design (DDD) Tactical Patterns

Modeling the fraud detection domain using DDD building blocks: Aggregates, Value Objects, Entities, and Domain Events.

## What This Solves

Fraud detection has complex business rules (velocity checks, geolocation calculations, risk scoring) that don't map well to simple CRUD or anemic domain models. DDD tactical patterns keep business logic explicit, encapsulated, and testable — the code speaks the same language as the business requirements.

## Building Blocks

### Entity — Identity That Persists

An object with a distinct identity maintained over time and across state changes.

```java
public class FraudCase {
    private final CaseId id;          // Identity matters
    private CaseStatus status;
    private final AnalystId assignedAnalyst;

    public void assignTo(AnalystId analyst) {
        this.status = CaseStatus.INVESTIGATING;
        // Domain event can be raised here
    }
}
```

### Value Object — Defined by Attributes, Immutable

An immutable object whose equality is based on its properties, not identity.

```java
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new InvalidAmountException("Amount cannot be negative");
    }
}

public record GeoLocation(double latitude, double longitude) {
    public double distanceTo(GeoLocation other) { /* Haversine formula */ }
}
```

### Aggregate — Consistency Boundary

A cluster of domain objects treated as a single unit. The aggregate root is the only way to modify anything inside the boundary.

```java
// Aggregate root in the Serverless Engine's domain.
public class FraudScore {
    private final TransactionId transactionId;
    private final double value;                         // clamped [0, 100]
    private final List<TriggeredRule> triggeredRules;   // rawEvidence persisted by the engine
    private final Instant decidedAt;

    public boolean opensCase(ScoreThreshold threshold) {
        return value >= threshold.value();
    }
}
```

The Serverless Engine emits `FraudScore`; the Core Backend consumes it and writes a `FraudCase`. Each aggregate is owned by exactly one service; cross-service consistency is achieved through the domain event, not by sharing aggregates.

### Domain Event — Something Meaningful Happened

An immutable record of something the business cares about.

```java
public record FraudAlertRaised(
    EventId eventId,
    TransactionId transactionId,
    FraudScore score,
    List<TriggeredRule> rules,
    Instant raisedAt
) implements DomainEvent {}
```

## Ubiquitous Language

The vocabulary shared between code and business stakeholders:

| Business Term | Code Representation | Purpose |
|---------------|-------------------|---------|
| **Transaction** | `Transaction` aggregate | The financial operation being evaluated for fraud |
| **FraudCase** | `FraudCase` aggregate | A collection of related suspicious transactions under investigation |
| **Rule** | `RuleEvaluator` interface | A condition that signals suspicious behavior (velocity, outlier, geo, blacklist) |
| **Score** | `FraudScore` value object | Numerical risk level (0-100) computed by the rule engine |
| **Alert** | `FraudAlert` entity | A notification generated when a transaction exceeds the risk threshold |
| **Analyst** | `AnalystId` value object | A human investigator who reviews flagged cases |
