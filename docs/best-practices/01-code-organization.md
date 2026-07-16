# Code Organization Standards

Standardized package and file naming to ensure every module is navigable without guessing.

## Module Package Structure

Every module in the monolith follows this exact convention:

```
src/main/java/com/centinela/<module>/
├── domain/
│   ├── model/           # Aggregates, Entities, Value Objects
│   ├── service/         # Domain services (business rules)
│   ├── event/           # Domain event records
│   └── port/            # Repository and notifier interfaces
├── application/
│   ├── service/         # Use case handlers (Command/Query handlers)
│   └── dto/             # Request and Response DTOs
└── infrastructure/
    ├── persistence/     # JPA entity mappings and repository implementations
    ├── messaging/       # Service Bus publisher and consumer classes
    └── web/             # REST controllers and exception handlers
```

## Package Details

| Package | What It Contains | Dependencies |
|---------|-----------------|--------------|
| `domain.model` | Aggregates, entities, value objects | None (pure Java) |
| `domain.service` | Stateless domain services | Only `domain.model` |
| `domain.event` | Domain event records | None (pure Java records) |
| `domain.port` | Interfaces for repositories, notifiers | Only `domain.model` |
| `application.service` | `@Service` command/query handlers | `domain.*` |
| `application.dto` | Request/Response data carriers | None (plain records) |
| `infrastructure.persistence` | `@Repository` JPA implementations | `domain.port` |
| `infrastructure.messaging` | Service Bus senders/receivers | `domain.port`, `domain.event` |
| `infrastructure.web` | `@RestController`, `@ControllerAdvice` | `application.dto` |

## File Naming Conventions

| Type | Naming Rule | Example |
|------|------------|---------|
| Aggregate | Business name | `Transaction.java`, `FraudCase.java` |
| Value Object | Business concept | `Money.java`, `GeoLocation.java`, `TransactionId.java` |
| Repository Port | `{Entity}Repository` interface | `TransactionRepository.java` |
| Repository Impl | `Jpa{Entity}Repository` | `JpaTransactionRepository.java` |
| REST Controller | `{Entity}Controller` | `TransactionController.java` |
| Domain Event | `{PastTenseVerb}.java` | `TransactionReceived.java`, `FraudAlertRaised.java` |
| DTO | `{Entity}{Request/Response}` | `TransactionRequest.java`, `CaseResponse.java` |
| Exception | `{Description}Exception` | `InsufficientTransactionDataException.java` |
