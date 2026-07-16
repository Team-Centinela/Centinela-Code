# Pipeline Pattern for Rule Engine

Chains fraud rules together so each rule can share context and influence subsequent evaluations — instead of evaluating each rule in isolation.

## What This Solves

The Strategy Pattern evaluates each fraud rule independently and sums the scores. This works for simple cases but cannot handle scenarios where:
- Rule A's output (e.g., "high velocity detected") should influence Rule B's threshold
- Evaluation should stop early if the score already exceeds the fraud threshold
- Rules need access to intermediate computations from previous rules

The Pipeline Pattern chains rules sequentially, passing a shared context between them.

## Architecture

```
Transaction ──▶ EvaluationContext ──▶ FR-1 (Velocity) ──▶ FR-2 (Outlier) ──▶ ... ──▶ Aggregated Score
                                          │                     │
                                          ▼                     ▼
                                     shares velocity       uses velocity data
                                     data in context       from context
```

## Implementation

### Evaluation Context — Shared State Between Rules

```java
public class EvaluationContext {
    private final Transaction transaction;
    private final Map<String, RuleResult> results = new LinkedHashMap<>();
    private final Map<String, Object> sharedData = new HashMap<>();

    public void addResult(String ruleCode, RuleResult result) {
        results.put(ruleCode, result);
    }

    public void shareData(String key, Object value) {
        sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, Class<T> type) {
        return type.cast(sharedData.get(key));
    }

    public FraudScore aggregateScore() {
        double total = results.values().stream()
            .mapToDouble(RuleResult::score)
            .sum();
        return new FraudScore(Math.min(total, 100.0), results.values());
    }
}
```

### Pipeline Stage Interface

```java
public interface PipelineStage {
    void process(EvaluationContext context);
    String getStageName();
}
```

### Pipeline Orchestrator

```java
@Component
public class FraudPipeline {
    private final List<PipelineStage> stages;

    public FraudPipeline(List<PipelineStage> stages) {
        this.stages = stages;
    }

    public FraudEvaluation evaluate(Transaction transaction) {
        EvaluationContext context = new EvaluationContext(transaction);

        for (PipelineStage stage : stages) {
            stage.process(context);
            // Optional: short-circuit if score already exceeds threshold
            if (context.aggregateScore().value() >= 100.0) break;
        }

        FraudScore score = context.aggregateScore();
        return new FraudEvaluation(transaction.id(), score, context.getResults());
    }
}
```

## Pipeline vs Strategy

| Capability | Strategy Pattern | Pipeline Pattern |
|-----------|----------------|-----------------|
| Rule execution | Independent | Chained with shared context |
| Evaluation order | Irrelevant | Configurable and matters |
| Short-circuit on high score | Not possible | Supported |
| Cross-rule data sharing | None | Full via `EvaluationContext` |
| Complexity | Lower | Slightly higher, more flexible |

## When to Use Pipeline

Use Pipeline when rules need intermediate state from each other or when evaluation order matters. For independent, stateless rules, Strategy Pattern is sufficient.
