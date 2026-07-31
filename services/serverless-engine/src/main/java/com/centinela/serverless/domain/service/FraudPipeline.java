package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.Recommendation;
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

    private final List<PipelineStage> stages1;
    private final List<PipelineStage> stages2;
    private final AggregatorStage aggregator;
    private final int scoreThreshold;

    /**
     * ADR-004 §4.1: the pipeline runs cheap rules (Stage 1) before the
     * expensive PostGIS-backed Stage 2 rules. Stage 2 is gated by a guard
     * ("any rule fired in Stage 1?") so the median evaluation cost stays
     * low — a transaction that triggers no Stage 1 rule (e.g. a vanilla
     * APPROVE) never pays for the FR-3 PostGIS scan.
     *
     * <p>SrLampi1001 review on PR #271 (comment 5143599891 item 1) flagged
     * that the v2 implementation collapsed this into a single flat stage
     * list. This constructor restores the stage-1/stage-2 split with the
     * guard branch.</p>
     */
    public FraudPipeline(List<PipelineStage> stages1,
                         List<PipelineStage> stages2,
                         AggregatorStage aggregator,
                         int scoreThreshold) {
        if (stages1 == null) {
            throw new IllegalArgumentException("stages1 must not be null");
        }
        if (stages2 == null) {
            throw new IllegalArgumentException("stages2 must not be null");
        }
        if (aggregator == null) {
            throw new IllegalArgumentException("aggregator must not be null");
        }
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new InvalidPipelineConfigException(
                    "scoreThreshold must be in [0, 100], got " + scoreThreshold);
        }
        this.stages1 = stages1;
        this.stages2 = stages2;
        this.aggregator = aggregator;
        this.scoreThreshold = scoreThreshold;
    }

    public FraudDecision execute(EvaluationContext ctx) {
        // === STAGE 1 (cheap, always) ===
        // FR-1 (Velocity) + FR-4 (High-Risk Merchant). ADR-004 §4.1 — short-circuit
        // per-rule (accumulatedScore >= SCORE_THRESHOLD) stops the loop the
        // moment a case is guaranteed.
        int triggeredBeforeStage1 = ctx.triggeredRules().size();
        runStage(ctx, stages1);

        // === GUARD: any rule fired in Stage 1? ===
        // ADR-004 §4.1 + SrLampi1001 review item 1: Stage 2 (FR-3 PostGIS +
        // FR-2 Atypical Amount) is the expensive branch. We pay for it only
        // when Stage 1 produced evidence worth investigating.
        boolean stageOneFired = ctx.triggeredRules().size() > triggeredBeforeStage1;
        if (stageOneFired) {
            // === STAGE 2 (expensive, conditional) ===
            runStage(ctx, stages2);
        }

        // === AGGREGATOR ===
        // Always runs per ADR-004 §4.1 (synthetic rule with score=0 when no
        // rule fired). The Aggregator is the canonical decision source per
        // ADR-004 §4.4 + SrLampi1001 review finding 3. The recommendation
        // travels on the returned TriggeredRule's rawEvidence map so the
        // FraudPipeline can read it on the SAME thread without going through
        // a singleton mutable field (which would race under concurrent calls).
        var aggregatorRule = aggregator.evaluate(ctx).orElseThrow();
        ctx.addTriggeredRule(aggregatorRule);
        Recommendation aggregatorRecommendation = Recommendation.valueOf(
                (String) aggregatorRule.rawEvidence().get("recommendation"));

        int totalScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        return FraudDecision.fromContext(
                ctx.sourceTransaction().transactionId(),
                ctx.triggeredRules(),
                aggregatorRecommendation,
                scoreThreshold,
                Instant.now()
        );
    }

    /**
     * Run a stage (Stage 1 or Stage 2) with per-rule short-circuit on
     * {@link #scoreThreshold}. Each {@link PipelineStage#evaluate} call writes
     * a {@link TriggeredRule} into the {@link EvaluationContext} (which updates
     * the accumulated score) when it fires; the loop breaks when the
     * accumulated score reaches {@code scoreThreshold}.
     *
     * <p>This is the ADR-004 §4.4 / #30 S-1 "single-threshold short-circuit":
     * one threshold, evaluated per stage, terminates the loop the moment a
     * case is guaranteed.</p>
     */
    private void runStage(EvaluationContext ctx, List<PipelineStage> stages) {
        for (PipelineStage stage : stages) {
            if (ctx.accumulatedScore() >= scoreThreshold) {
                break;
            }
            Optional<TriggeredRule> result = stage.evaluate(ctx);
            result.ifPresent(ctx::addTriggeredRule);
        }
    }

    public int scoreThreshold() {
        return scoreThreshold;
    }

    public List<PipelineStage> stages1() {
        return List.copyOf(stages1);
    }

    public List<PipelineStage> stages2() {
        return List.copyOf(stages2);
    }

    public static final class InvalidPipelineConfigException extends RuntimeException {
        public InvalidPipelineConfigException(String message) {
            super(message);
        }
    }
}
