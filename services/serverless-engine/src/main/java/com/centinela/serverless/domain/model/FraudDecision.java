package com.centinela.serverless.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudDecision(
        UUID transactionId,
        int totalScore,
        List<TriggeredRule> triggeredRules,
        Recommendation recommendation,
        int scoreThreshold,
        Instant evaluatedAt
) {
    public FraudDecision {
        if (transactionId == null) throw new IllegalArgumentException("transactionId must not be null");
        if (totalScore < 0 || totalScore > 100) throw new IllegalArgumentException("totalScore must be between 0 and 100");
        if (triggeredRules == null) throw new IllegalArgumentException("triggeredRules must not be null");
        if (recommendation == null) throw new IllegalArgumentException("recommendation must not be null");
        if (scoreThreshold < 0 || scoreThreshold > 100) throw new IllegalArgumentException("scoreThreshold must be in [0, 100]");
        if (evaluatedAt == null) throw new IllegalArgumentException("evaluatedAt must not be null");
    }

    /**
     * Builds a {@link FraudDecision} using the recommendation already computed
     * by the {@code AggregatorStage} (so {@code FraudDecision.recommendation},
     * the raw {@code triggered_rules} evidence, and the
     * {@code FraudEvaluationCompleted.recommendation} envelope CANNOT
     * disagree).
     *
     * <p>This is the ADR-004 §4.4 contract + SrLampi1001 review on PR #268
     * (finding 3) fix: the previous version recomputed the recommendation
     * via a hardcoded {@code Recommendation.fromScore(totalScore)} helper
     * (BLOCK &gt;= 70, FLAG &gt;= 30), ignoring the aggregator's configured
     * thresholds. With a configured {@code scoreThreshold=80} and
     * {@code totalScore=75}, the aggregator correctly computed {@code FLAG}
     * but {@code FraudDecision} emitted {@code BLOCK} — a silent inconsistency.
     * The hardcoded helper was deleted in PR #271 review item 3.</p>
     *
     * <p>{@code scoreThreshold} is recorded on the decision so downstream
     * consumers (Reporting, analytics, the explainer template) see which
     * threshold value was applied at evaluation time without re-reading
     * {@code rules_config}.</p>
     */
    public static FraudDecision fromContext(UUID transactionId,
                                            List<TriggeredRule> triggeredRules,
                                            Recommendation aggregatorRecommendation,
                                            int scoreThreshold,
                                            Instant evaluatedAt) {
        int totalScore = triggeredRules.stream().mapToInt(TriggeredRule::score).sum();
        if (totalScore > 100) totalScore = 100;
        return new FraudDecision(transactionId, totalScore, triggeredRules,
                aggregatorRecommendation, scoreThreshold, evaluatedAt);
    }
}
