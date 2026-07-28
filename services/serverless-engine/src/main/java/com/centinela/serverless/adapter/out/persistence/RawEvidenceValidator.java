package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.model.TriggeredRule;

import java.util.Map;
import java.util.Set;

public final class RawEvidenceValidator {

    // ADR-004 §4.2: each RuleResult whose ruleCode is below MUST carry exactly
    // these keys in rawEvidence (subsequent keys are allowed; missing required keys
    // are rejected here so a future rule implementation that drops the schema
    // surfaces at unit-test time instead of silently producing an unexplainable
    // TriggeredRule.
    private static final Map<String, Set<String>> FR_SCHEMAS = Map.of(
            "FR-1", Set.of("window_seconds", "txn_count", "threshold", "current_score_added"),
            "FR-2", Set.of("historical_avg_usd", "historical_sample_size",
                           "current_amount_usd", "std_dev_usd", "z_score", "current_score_added"),
            "FR-3", Set.of("last_txn_lat", "last_txn_lon", "last_txn_at",
                           "current_lat", "current_lon",
                           "distance_km", "elapsed_seconds",
                           "implied_speed_kmh", "max_allowed_speed_kmh", "current_score_added"),
            "FR-4", Set.of("merchant_id", "risk_label", "current_score_added")
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
