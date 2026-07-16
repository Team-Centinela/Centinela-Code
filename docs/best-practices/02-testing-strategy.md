# Testing Strategy

Testing approach organized by architectural layer with specific coverage targets for each.

## Coverage Targets by Layer

| Layer | Target | Rationale |
|-------|--------|-----------|
| **Domain** | 100% | Business rules for fraud detection must be correct — no exceptions |
| **Application** | 90% | Orchestration logic should be well-covered; some error paths acceptable |
| **Infrastructure** | 80% | Framework integration (JPA, Service Bus) is mostly generated/boilerplate |

## What to Test at Each Layer

### Domain — Pure Unit Tests

Test business rules in isolation. No Spring context, no database, no network.

```java
class TransactionTest {
    @Test
    void should_flag_transaction_when_amount_exceeds_threshold() {
        Transaction tx = new Transaction(
            new TransactionId("tx-1"),
            new Money(new BigDecimal("15000"), Currency.USD),
            // ...
        );
        FraudEvaluation eval = tx.evaluate(mock(RuleEngine.class));
        assertThat(eval.isFraudulent()).isTrue();
    }

    @Test
    void should_reject_negative_amount() {
        assertThrows(InvalidAmountException.class, () ->
            new Money(new BigDecimal("-100"), Currency.USD));
    }
}
```

### Application — Unit Tests with Mocks

Test use case handlers with mocked ports.

```java
@ExtendWith(MockitoExtension.class)
class EvaluateTransactionHandlerTest {
    @Mock TransactionRepository txRepo;
    @Mock RuleEngine ruleEngine;
    @Mock EventPublisher eventPublisher;

    @Test
    void should_publish_event_on_successful_evaluation() {
        when(txRepo.findById(any())).thenReturn(Optional.of(aTransaction()));
        handler.handle(new EvaluateTransaction("tx-1"));
        verify(eventPublisher).publish(any(FraudEvaluationCompleted.class));
    }
}
```

### Infrastructure — Integration Tests

Test adapter implementations with real framework integration (test database, embedded broker).

```java
@SpringBootTest
@AutoConfigureTestDatabase
class JpaTransactionRepositoryTest {
    @Autowired private JpaTransactionRepository repository;

    @Test
    void should_persist_and_retrieve_transaction() {
        Transaction tx = aTransaction();
        repository.save(tx);
        assertThat(repository.findById(tx.id())).isPresent();
    }
}
```

## Test Naming

```
Class: {TargetClass}Test

Method: should_{expected_behavior}_when_{scenario}
Example: should_return_empty_when_transaction_not_found
Example: should_throw_exception_when_amount_is_negative
```

## Test Organization in the Module

```
src/test/java/com/centinela/<module>/
├── domain/
│   └── model/           # Tests for aggregates and value objects
│       └── TransactionTest.java
├── application/
│   └── service/         # Tests for use case handlers
│       └── EvaluateTransactionHandlerTest.java
└── infrastructure/
    ├── persistence/     # Integration tests for JPA adapters
    │   └── JpaTransactionRepositoryTest.java
    └── web/             # Tests for REST controllers
        └── TransactionControllerTest.java
```
