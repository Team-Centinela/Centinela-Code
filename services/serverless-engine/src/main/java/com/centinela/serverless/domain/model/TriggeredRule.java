package com.centinela.serverless.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

public record TriggeredRule(
        String ruleCode,
        int score,
        Map<String, Object> rawEvidence,
        Instant evaluatedAt
) {
    public TriggeredRule {
        if (ruleCode == null || ruleCode.isBlank()) throw new IllegalArgumentException("ruleCode must not be blank");
        if (score < 0) throw new IllegalArgumentException("score must not be negative");
        if (rawEvidence == null) throw new IllegalArgumentException("rawEvidence must not be null");
        if (evaluatedAt == null) throw new IllegalArgumentException("evaluatedAt must not be null");
    }
}
