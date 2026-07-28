package com.centinela.serverless.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudDecision(
        UUID transactionId,
        int totalScore,
        List<TriggeredRule> triggeredRules,
        Recommendation recommendation,
        Instant evaluatedAt
) {
    public FraudDecision {
        if (transactionId == null) throw new IllegalArgumentException("transactionId must not be null");
        if (totalScore < 0 || totalScore > 100) throw new IllegalArgumentException("totalScore must be between 0 and 100");
        if (triggeredRules == null) throw new IllegalArgumentException("triggeredRules must not be null");
        if (recommendation == null) throw new IllegalArgumentException("recommendation must not be null");
        if (evaluatedAt == null) throw new IllegalArgumentException("evaluatedAt must not be null");
    }

    public static FraudDecision fromContext(UUID transactionId, java.util.List<TriggeredRule> triggeredRules, Instant evaluatedAt) {
        int totalScore = triggeredRules.stream().mapToInt(TriggeredRule::score).sum();
        if (totalScore > 100) totalScore = 100;
        return new FraudDecision(transactionId, totalScore, triggeredRules, Recommendation.fromScore(totalScore), evaluatedAt);
    }
}
