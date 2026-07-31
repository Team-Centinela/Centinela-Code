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
     * constructor so both components see the same value. The Aggregator
     * does NOT load {@code scoreThreshold} from its own rules_config row —
     * doing so would re-introduce the two-threshold drift ADR-004 §4.4 / #30
     * S-1 fix explicitly forbids.
     *
     * <p>The Aggregator still owns its {@code flagThreshold} (the soft
     * "needs review" floor). Validation per SrLampi1001 review on PR #268
     * (finding 1 action 3): {@code 0 <= flagThreshold < scoreThreshold <= 100}
     * with STRICT inequality — equality would collapse FLAG and BLOCK into
     * the same recommendation.</p>
     */
    private final int scoreThreshold;
    private final int flagThreshold;
    private Recommendation lastRecommendation = Recommendation.APPROVE;

    public AggregatorStage(int scoreThreshold, int flagThreshold) {
        validate(scoreThreshold, flagThreshold);
        this.scoreThreshold = scoreThreshold;
        this.flagThreshold = flagThreshold;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        int clampedScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        Recommendation rec = computeRecommendation(clampedScore, flagThreshold, scoreThreshold);
        lastRecommendation = rec;

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", clampedScore);
        evidence.put("recommendation", rec.name());
        evidence.put("rulesTriggered", ctx.triggeredRules().size());
        evidence.put("flagThreshold", flagThreshold);
        evidence.put("scoreThreshold", scoreThreshold);

        return Optional.of(new TriggeredRule(RULE_CODE, 0, evidence, Instant.now()));
    }

    /**
     * Canonical decision for this aggregator instance, recomputed on every
     * {@link #evaluate(EvaluationContext)} call. {@link FraudPipeline} reads
     * this value so {@link com.centinela.serverless.domain.model.FraudDecision}
     * carries the same recommendation as the raw evidence + the
     * FraudEvaluationCompleted envelope — eliminating the hardcoded 70/30
     * drift flagged by SrLampi1001 (PR #268 finding 3).
     */
    public Recommendation lastRecommendation() {
        return lastRecommendation;
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
