# Hexagonal Architecture (Ports & Adapters)

Each module is organized using Hexagonal Architecture: **business logic at the center**, infrastructure plugged in through interfaces (ports) and implementations (adapters).

## What This Solves

In traditional MVC, business logic is scattered across Service classes, tightly coupled to Spring and JPA annotations. Testing requires loading the full Spring context. Changing the database, framework, or messaging system risks breaking business rules. Hexagonal Architecture inverts this: the domain has zero dependencies, so it can be tested in milliseconds and changed independently of infrastructure.

## How It Works

```
                    ┌──────────────────────┐
                    │      DOMAIN           │  ← Pure Java, no imports from Spring/JPA/Azure
                    │  (Entities, Rules,    │
                    │   Domain Services)    │
                    └──────┬───────────┬────┘
                           │           │
                    ┌──────┴────┐ ┌────┴──────┐
                    │  INPUT    │ │  OUTPUT   │  ← Ports (Java interfaces)
                    │  PORTS    │ │  PORTS    │
                    └──────┬────┘ └────┬──────┘
                           │           │
                    ┌──────┴────┐ ┌────┴──────┐
                    │   Web    │ │    DB     │  ← Adapters (implement ports)
                    │Controller│ │ JPA Repo  │
                    └──────────┘ └───────────┘
```

## The Three Layers

### 1. Domain (Innermost — Zero Dependencies)

Pure Java objects containing business logic. No Spring, no JPA, no Azure imports.

```java
public class Transaction {
    private final TransactionId id;
    private final Money amount;
    private final GeoLocation location;

    public FraudRisk evaluate(RuleEngine engine) {
        return engine.assess(this);
    }
}
```

### 2. Application (Ports)

Interfaces that define what the domain needs from the outside world.

```java
// Output port — the domain needs to persist transactions
public interface TransactionRepository {
    void save(Transaction transaction);
    Optional<Transaction> findById(TransactionId id);
}
```

### 3. Infrastructure (Adapters)

Concrete implementations of ports using specific technologies.

```java
@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final JpaTransactionEntityRepo springRepo;

    @Override
    public void save(Transaction transaction) {
        springRepo.save(TransactionMapper.toEntity(transaction));
    }
}
```

## Why This Over MVC

| Concern | MVC | Hexagonal |
|---------|-----|-----------|
| Where business logic lives | Scattered across Service classes | Centralized in Domain |
| Testing business logic | Needs Spring Boot test context | Pure JUnit, no framework |
| Changing database | Affects Service layer | Only replace JpaTransactionRepository |
| Adding new delivery mechanism (e.g., GraphQL) | New controller, may duplicate logic | New adapter implementing same port |
| Framework upgrade risk | Annotations everywhere | Only adapter layer changes |
