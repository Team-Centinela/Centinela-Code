# Logging and Monitoring Standards

Structured logging, metric collection, and health check conventions for observability in Azure Application Insights.

## Structured Logging

All logs must be structured JSON with key-value pairs, not plain text messages.

```java
import net.logstash.logback.argument.StructuredArguments;

private static final Logger log = LoggerFactory.getLogger(TransactionModule.class);

public void evaluate(Transaction tx) {
    log.info("Evaluating transaction",
        StructuredArguments.kv("transactionId", tx.id().value()),
        StructuredArguments.kv("amount", tx.amount().toString()),
        StructuredArguments.kv("accountId", tx.accountId().value())
    );
}
// Produces: {"message":"Evaluating transaction","transactionId":"tx-123","amount":"USD 1500.00","accountId":"acc-456"}
```

## Log Levels

| Level | When to Use |
|-------|-------------|
| `ERROR` | A business operation failed and cannot recover (rule evaluation failed, database unreachable) |
| `WARN` | An operation succeeded but encountered an unusual condition (retry attempted, deprecated API used) |
| `INFO` | Significant business milestone (transaction evaluated, fraud case created, alert sent) |
| `DEBUG` | Detailed diagnostic information — enabled temporarily for troubleshooting |
| `TRACE` | Step-by-step execution flow — enabled temporarily for development debugging |

## What to Include

**Always log:** transaction ID, case ID, correlation ID, operation name, duration, result (success/failure).

**Never log:** passwords, secrets, API keys, credit card numbers, PII, internal tokens, connection strings.

## Custom Metrics

Collected through Azure Application Insights for dashboards and alerting.

| Metric | Type | Description |
|--------|------|-------------|
| `transactions.received` | Counter | Total transactions submitted to the system |
| `transactions.evaluated` | Counter | Total transactions processed through the rule engine |
| `fraud.cases.created` | Counter | New fraud investigation cases opened |
| `rule.{code}.triggered` | Counter | Times a specific fraud rule triggered (one metric per rule) |
| `evaluation.duration` | Histogram | Milliseconds to evaluate a single transaction through the full pipeline |
| `ingestion.latency` | Histogram | Milliseconds from HTTP receipt to Service Bus publication |

## Health Endpoints

```
GET /health          → {"status": "UP"}
GET /health/readiness → {
    "status": "UP",
    "dependencies": {
        "postgresql": { "status": "UP", "latencyMs": 5 },
        "serviceBus": { "status": "UP" },
        "keyVault":   { "status": "UP" }
    }
}
GET /health/liveness  → {"status": "UP"}
```
