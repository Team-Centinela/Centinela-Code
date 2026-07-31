package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class FraudPipeline {

    /**
     * ADR-004 §4.4 + #237: the early-break pivot for the pipeline. When the
     * accumulated score crosses this threshold the loop stops evaluating
     * further rules (they would not change the case-creation decision, and
     * the most expensive stage — FR-3 PostGIS — has not been reached yet on
     * the cheap-first ordering). Renamed from {@code DEFAULT_SCORE_THRESHOLD}
     * in commit d0591c7-pre to make the role explicit: this is the
     * short-circuit pivot, NOT the case-creation gate.
     */
    static final int DEFAULT_SHORT_CIRCUIT_THRESHOLD = 50;
    static final String PIPELINE_CONFIG_CODE = "PIPELINE";

    private final List<PipelineStage> stages;
    private final AggregatorStage aggregator;
    private final RuleConfigRepository configRepo;
    private final int shortCircuitThreshold;

    public FraudPipeline(List<PipelineStage> stages, AggregatorStage aggregator, RuleConfigRepository configRepo) {
        this.stages = stages;
        this.aggregator = aggregator;
        this.configRepo = configRepo;
        this.shortCircuitThreshold = loadShortCircuitThreshold();
    }

    public FraudDecision execute(EvaluationContext ctx) {
        for (PipelineStage stage : stages) {
            if (ctx.accumulatedScore() >= shortCircuitThreshold) {
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
                Instant.now()
        );
    }

    int shortCircuitThreshold() {
        return shortCircuitThreshold;
    }

    private int loadShortCircuitThreshold() {
        var opt = configRepo.findByRuleCode(PIPELINE_CONFIG_CODE);
        int value;
        if (opt.isEmpty() || !opt.get().enabled()) {
            value = DEFAULT_SHORT_CIRCUIT_THRESHOLD;
        } else {
            // Config key was previously 'scoreThreshold'; renamed to
            // 'shortCircuitThreshold' per #237. ADR-004 §4.4 single-threshold
            // model is split into shortCircuit + caseCreation so analysts can
            // tune them independently.
            value = opt.get().getInt("shortCircuitThreshold", DEFAULT_SHORT_CIRCUIT_THRESHOLD);
        }
        // Fail-closed validation per #238 (commit a2b3c4-pre): negative
        // shortCircuitThreshold would cause the loop to never break (rules
        // keep firing until the Aggregator). Surface the bad config at
        // construction time so a misconfigured pipeline fails fast at boot
        // rather than silently over-evaluating.
        if (value < 0) {
            throw new InvalidPipelineConfigException(
                    "shortCircuitThreshold must be >= 0, got " + value);
        }
        return value;
    }

    public static final class InvalidPipelineConfigException extends RuntimeException {
        public InvalidPipelineConfigException(String message) {
            super(message);
        }
    }
}
