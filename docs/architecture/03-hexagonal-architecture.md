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

## Package Convention (all modules)

Every module under `modules/<module-name>/` follows the same four-package layout:

```
com.centinela.core.modules.<module>/
  domain/           # Pure Java aggregates, value objects, domain services
  application/      # Use cases / inbound ports (interfaces)
  port/             # Outbound port interfaces
  adapter/
    persistence/    # JPA / JDBC adapters implementing port interfaces
    messaging/      # Outbox publisher, event listeners (Spring events + Service Bus)
    rest/           # REST controllers (only if module exposes HTTP endpoints)
```

**Concrete example — scoring module:**

```
com.centinela.core.modules.scoring/
  domain/
    FraudScore.java              # value object
    RuleEngine.java              # domain service — pure Java, no Spring
  application/
    ScoreTransactionUseCase.java # inbound port interface
  port/
    TransactionRepository.java   # outbound port interface
    RuleConfigProvider.java      # outbound port interface
  adapter/
    persistence/
      JpaTransactionRepository.java   # @Repository implements TransactionRepository
      JpaRuleConfigRepository.java    # @Repository implements RuleConfigProvider
    messaging/
      ScoreCompletedPublisher.java    # publishes via outbox on ApplicationEvent
    rest/
      ScoreAdminController.java       # GET /admin/scores — admin-only
```

### Rules

1. **No cross-module imports of `domain/` or `application/`.** Module A must never `import com.centinela.core.modules.b.domain.*`. Cross-module coordination happens through domain events only.
2. **Domain layer has zero framework imports.** No Spring, JPA, or Azure SDK annotations in `domain/`.
3. **Ports are Java interfaces.** `application/` contains interfaces the module exposes; `port/` contains interfaces the module depends on.
4. **Adapters are the only layer with framework imports.** `adapter/persistence/` may use JPA, `adapter/messaging/` may use Spring events and Azure SDK.
