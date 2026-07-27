package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.model.TriggeredRule;

import java.util.Map;
import java.util.Set;

public final class RawEvidenceValidator {

    private static final Map<String, Set<String>> FR_SCHEMAS = Map.of(
            "FR-1", Set.of("windowMinutes", "transactionCount", "threshold"),
            "FR-2", Set.of("amount", "historicalAvg", "historicalStdDev", "zScore", "threshold"),
            "FR-3", Set.of("currentLocation", "previousLocation", "distanceKm", "timeDeltaMinutes", "maxPossibleKm", "physicallyImpossible"),
            "FR-4", Set.of("merchantId", "flagged", "flaggedSince")
    );

    private RawEvidenceValidator() {}

    public static void validate(TriggeredRule rule) {
        Set<String> requiredKeys = FR_SCHEMAS.get(rule.ruleCode());
        if (requiredKeys == null) return;

        for (String key : requiredKeys) {
            if (!rule.rawEvidence().containsKey(key)) {
                throw new IllegalArgumentException(
                        "Missing required key '%s' in rawEvidence for rule %s".formatted(key, rule.ruleCode()));
            }
        }
    }

    public static void validateAll(Iterable<TriggeredRule> rules) {
        for (TriggeredRule rule : rules) {
            validate(rule);
        }
    }
}
