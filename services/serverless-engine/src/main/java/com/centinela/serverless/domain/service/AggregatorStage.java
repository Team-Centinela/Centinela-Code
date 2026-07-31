package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AggregatorStage implements PipelineStage {

    public static final String RULE_CODE = "AGGREGATOR";
    public static final int DEFAULT_FLAG_THRESHOLD = 30;

    /**
     * ADR-004 §4.4: {@code scoreThreshold} (default 70) is the single canonical
     * threshold shared with {@link FraudPipeline}. The pipeline reads it from
     * the PIPELINE {@code rules_config} row and passes it to the Aggregator
     * constructor so both components see the same value.
     *
     * <p>The Aggregator's {@code flagThreshold} (soft "needs review" floor)
     * validation per SrLampi1001 review on PR #268 (finding 1 action 3):
     * {@code 0 <= flagThreshold < scoreThreshold <= 100} with STRICT inequality
     * — equality would collapse FLAG and BLOCK into the same recommendation.</p>
     *
     * <p>Thread-safety note: the Aggregator is a stateless rule. The
     * recommendation is carried on the returned {@link TriggeredRule}'s
     * {@code rawEvidence} map, NOT in a singleton field. {@link FraudPipeline}
     * reads the recommendation from that map, eliminating the singleton-field
     * race that SrLampi1001's concurrency probe (PR #271) exposed.</p>
     */
    private final int scoreThreshold;
    private final int flagThreshold;

    public AggregatorStage(int scoreThreshold, int flagThreshold) {
        validate(scoreThreshold, flagThreshold);
        this.scoreThreshold = scoreThreshold;
        this.flagThreshold = flagThreshold;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        int clampedScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        Recommendation rec = computeRecommendation(clampedScore, flagThreshold, scoreThreshold);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", clampedScore);
        evidence.put("recommendation", rec.name());
        evidence.put("rulesTriggered", ctx.triggeredRules().size());
        evidence.put("flagThreshold", flagThreshold);
        evidence.put("scoreThreshold", scoreThreshold);

        return Optional.of(new TriggeredRule(RULE_CODE, 0, evidence, Instant.now()));
    }

    /**
     * Helper for callers that need the canonical recommendation outside of
     * the {@code rawEvidence} flow. Pure function — no instance state, so
     * thread-safe by construction.
     */
    public Recommendation computeRecommendation(int totalScore) {
        return computeRecommendation(totalScore, flagThreshold, scoreThreshold);
    }

    public int flagThreshold() {
        return flagThreshold;
    }

    public int scoreThreshold() {
        return scoreThreshold;
    }

    static Recommendation computeRecommendation(int score, int flagThreshold, int scoreThreshold) {
        if (score >= scoreThreshold) return Recommendation.BLOCK;
        if (score >= flagThreshold) return Recommendation.FLAG;
        return Recommendation.APPROVE;
    }

    /**
     * Fail-closed validator per SrLampi1001 review (PR #268 finding 1 action 3)
     * + ADR-004 §4.4. Strict {@code flagThreshold < scoreThreshold}: equality
     * would mean "any score that triggers FLAG also triggers BLOCK", collapsing
     * the two recommendations.
     */
    static void validate(int scoreThreshold, int flagThreshold) {
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new InvalidAggregatorConfigException(
                    "scoreThreshold must be in [0, 100], got " + scoreThreshold);
        }
        if (flagThreshold < 0 || flagThreshold > 100) {
            throw new InvalidAggregatorConfigException(
                    "flagThreshold must be in [0, 100], got " + flagThreshold);
        }
        if (flagThreshold >= scoreThreshold) {
            throw new InvalidAggregatorConfigException(
                    "flagThreshold (" + flagThreshold
                            + ") must be < scoreThreshold (" + scoreThreshold + ")");
        }
    }

    public static final class InvalidAggregatorConfigException extends RuntimeException {
        public InvalidAggregatorConfigException(String message) {
            super(message);
        }
    }
}
