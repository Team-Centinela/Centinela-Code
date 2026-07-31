package com.centinela.cases.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Case(
        UUID id,
        UUID transactionId,
        String accountId,
        CaseStatus status,
        int score,
        String recommendation,
        List<Map<String, Object>> triggeredRules,
        Instant openedAt,
        Instant resolvedAt,
        String resolvedBy,
        String resolutionNotes,
        UUID correlationId,
        String traceId
) {
    public Case {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(accountId, "accountId must not be blank");
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be within [0, 100]");
        }
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        if (recommendation.isBlank()) {
            throw new IllegalArgumentException("recommendation must not be blank");
        }
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        triggeredRules = triggeredRules == null ? List.of() : List.copyOf(triggeredRules);
    }

    public boolean isOpen() {
        return status == CaseStatus.OPEN || status == CaseStatus.IN_REVIEW;
    }
}
