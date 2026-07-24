package com.centinela.serverless.domain.model;

import java.util.List;
import java.util.UUID;

public record FraudScore(
        UUID transactionId,
        double score,
        boolean flagged,
        List<TriggeredRule> triggeredRules,
        String explanation
) {

    public boolean hasAnyFired() {
        return triggeredRules != null && triggeredRules.stream().anyMatch(TriggeredRule::isFired);
    }

    public FraudScore withClampedScore(double max) {
        double s = Math.max(0.0d, Math.min(max, score));
        if (s == score) {
            return this;
        }
        return new FraudScore(transactionId, s, flagged, triggeredRules, explanation);
    }
}
