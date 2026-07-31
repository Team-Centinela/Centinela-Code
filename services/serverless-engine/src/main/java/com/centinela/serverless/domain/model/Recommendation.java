package com.centinela.serverless.domain.model;

public enum Recommendation {
    APPROVE,
    FLAG,
    BLOCK;

    /**
     * @deprecated Do not call directly from business code. The canonical
     * decision is computed by {@code AggregatorStage} using the configured
     * {@code scoreThreshold} and {@code flagThreshold}, and surfaced via
     * {@code AggregatorStage.lastRecommendation()}. {@code FraudDecision}
     * consumes that value directly per ADR-004 §4.4 (SrLampi1001 review
     * finding 3 on PR #268). This factory is kept only for backwards
     * compatibility with tests; do NOT add new callers.
     */
    @Deprecated
    public static Recommendation fromScore(int totalScore) {
        // Hardcoded fallback (BLOCK >= 70, FLAG >= 30) — preserved as a last
        // resort when the aggregator's recommendation is unavailable. New
        // code must go through AggregatorStage.lastRecommendation() so the
        // configured scoreThreshold / flagThreshold take effect.
        if (totalScore >= 70) return BLOCK;
        if (totalScore >= 30) return FLAG;
        return APPROVE;
    }
}
