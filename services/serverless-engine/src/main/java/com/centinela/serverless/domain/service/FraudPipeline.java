package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.TriggeredRule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class FraudPipeline {

    /**
     * ADR-004 §4.4: {@code SCORE_THRESHOLD} is the single canonical threshold
     * used by BOTH the pipeline short-circuit pivot AND the case-creation gate.
     * Default 70 matches the ADR; tunable via the PIPELINE {@code rules_config}
     * row ({@code scoreThreshold} key). Per SrLampi1001 review on PR #268:
     * "Under the current ADR, use one canonical threshold (70) for both
     * pipeline termination and case creation."
     *
     * <p>The previous §0.2.3 split ({@code shortCircuitThreshold=50},
     * {@code caseCreationThreshold=70}) introduced a false-negative bug:
     * a probe with stages {@code 30 + 25 + 25} (= 80) returned {@code 55 / FLAG}
     * because the pipeline broke at 50 before the case-creation gate was
     * reachable. The split also re-introduces the two-threshold drift that
     * ADR-004 §4.4 / #30 S-1 fix explicitly calls out as a bug to avoid.</p>
     */
    public static final int DEFAULT_SCORE_THRESHOLD = 70;
    public static final String PIPELINE_CONFIG_CODE = "PIPELINE";

    private final List<PipelineStage> stages;
    private final AggregatorStage aggregator;
    private final int scoreThreshold;

    public FraudPipeline(List<PipelineStage> stages,
                         AggregatorStage aggregator,
                         int scoreThreshold) {
        // Threshold comes from the constructor so the canonical value is
        // loaded ONCE in RulePipelineWiring (and passed to both AggregatorStage
        // and FraudPipeline). This is the ADR-004 §4.4 "single threshold"
        // contract.
        if (stages == null) {
            throw new IllegalArgumentException("stages must not be null");
        }
        if (aggregator == null) {
            throw new IllegalArgumentException("aggregator must not be null");
        }
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new InvalidPipelineConfigException(
                    "scoreThreshold must be in [0, 100], got " + scoreThreshold);
        }
        this.stages = stages;
        this.aggregator = aggregator;
        this.scoreThreshold = scoreThreshold;
    }

    public FraudDecision execute(EvaluationContext ctx) {
        for (PipelineStage stage : stages) {
            if (ctx.accumulatedScore() >= scoreThreshold) {
                break;
            }
            Optional<TriggeredRule> result = stage.evaluate(ctx);
            result.ifPresent(ctx::addTriggeredRule);
        }

        aggregator.evaluate(ctx).ifPresent(ctx::addTriggeredRule);

        int totalScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        return FraudDecision.fromContext(
                ctx.sourceTransaction().transactionId(),
                ctx.triggeredRules(),
                // Aggregator is the canonical decision source per ADR-004 §4.4 +
                // SrLampi1001 review finding 3: FraudDecision MUST consume the
                // aggregator's recommendation to avoid the hardcoded 70/30
                // drift in Recommendation.fromScore.
                aggregator.lastRecommendation(),
                scoreThreshold,
                Instant.now()
        );
    }

    public int scoreThreshold() {
        return scoreThreshold;
    }

    public static final class InvalidPipelineConfigException extends RuntimeException {
        public InvalidPipelineConfigException(String message) {
            super(message);
        }
    }
}
