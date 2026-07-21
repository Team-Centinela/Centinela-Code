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
Transaction ──▶ EvaluationContext ──▶ STAGE 1 (cheap, always)
                                          │     FR-1 (Velocity) → FR-4 (High-Risk Merchant)
                                          ▼
                                       guard: did Stage 1 fire?
                                          │       │
                                          │       └── yes ──▶ STAGE 2 (expensive, conditional)
                                          │                       FR-3 (Impossible Geo) → FR-2 (Atypical)
                                          ▼
                              Aggregator (sum, clamp to [0,100], case opens iff ≥ SCORE_THRESHOLD)
```

Stage assignment is fixed by `../decision-log/ADR-004-rule-engine-pipeline-explainer.md` §4.1. FR-3 is in Stage 2 because PostGIS `ST_Distance` is the most expensive operation on B1ms-shaped data — gating it behind a Stage-1 hit keeps median evaluation cost low. See ADR-004 for the cost table and the rationale.

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
    private final List<PipelineStage> stage1;   // cheap: FR-1, FR-4
    private final List<PipelineStage> stage2;   // expensive: FR-3, FR-2
    private final ThresholdsConfig thresholds;  // score_threshold from system_config (default 70)

    public FraudEvaluation evaluate(Transaction transaction) {
        EvaluationContext context = new EvaluationContext(transaction);

        for (PipelineStage stage : stage1) {
            stage.process(context);
            if (context.aggregateScore().value() >= thresholds.caseOpensAt()) break;
        }

        boolean stageOneFired = context.getResults().values().stream()
                .anyMatch(r -> r.scoreAdded() > 0);

        if (stageOneFired) {
            for (PipelineStage stage : stage2) {
                stage.process(context);
                if (context.aggregateScore().value() >= thresholds.caseOpensAt()) break;
            }
        }

        FraudScore score = context.aggregateScore();   // clamped to [0,100]
        boolean opensCase = score.value() >= thresholds.caseOpensAt();
        return new FraudEvaluation(transaction.id(), score, opensCase, context.getResults());
    }
}
```

> The single short-circuit termination rule is `aggregateScore() >= SCORE_THRESHOLD` (default 70), evaluated **after** every stage whether or not it produced a new `RuleResult`. The clamp to `[0, 100]` happens once at the aggregator. See `../decision-log/ADR-004-rule-engine-pipeline-explainer.md` §4.1 / §4.4 for the full semantics.

## `rawEvidence` Schema — Pinned Per Rule

The deterministic NL Explainer (`RuleExplanationTemplates.java`) is forbidden from querying the database or any external source at render time. Every value a template needs must already exist on the `RuleResult.rawEvidence` of the rule that fired. The per-rule key contract (mirrors ADR-004 §4.2):

| Rule code | Required keys in `rawEvidence` JSONB |
|---|---|
| **FR-1 Velocity** | `window_seconds`, `txn_count`, `threshold`, `current_score_added` |
| **FR-2 Atypical Amount** | `historical_avg_usd`, `historical_sample_size`, `current_amount_usd`, `std_dev_usd`, `z_score`, `current_score_added` |
| **FR-3 Impossible Geo** | `last_txn_lat`, `last_txn_lon`, `last_txn_at`, `current_lat`, `current_lon`, `distance_km`, `elapsed_seconds`, `implied_speed_kmh`, `max_allowed_speed_kmh`, `current_score_added` |
| **FR-4 High-Risk Merchant** | `merchant_id`, `risk_label`, `current_score_added` |

Unit tests assert that each rule's payload contains exactly the listed keys before the `RuleResult` is published; a missing key fails the test and triggers a `RULE_EVIDENCE_PAYLOAD_INVALID` runtime log line. Renaming a key is an ADR amendment.

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
