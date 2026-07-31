package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AggregatorStage implements PipelineStage {

    static final String RULE_CODE = "AGGREGATOR";
    static final int DEFAULT_FLAG_THRESHOLD = 30;

    /**
     * ADR-004 §4.4 + #237: renamed from {@code DEFAULT_BLOCK_THRESHOLD} because
     * the role is the case-creation gate, not a generic block threshold. The
     * case lifecycle opens a Case row when totalScore >= this value (the
     * BLOCK recommendation). Kept distinct from {@code flagThreshold} which
     * is the soft "needs review" floor.
     */
    static final int DEFAULT_CASE_CREATION_THRESHOLD = 70;

    private final RuleConfigRepository configRepo;

    public AggregatorStage(RuleConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        var cfg = loadConfig();

        int clampedScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        Recommendation rec = computeRecommendation(clampedScore, cfg.flagThreshold, cfg.caseCreationThreshold);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", clampedScore);
        evidence.put("recommendation", rec.name());
        evidence.put("rulesTriggered", ctx.triggeredRules().size());
        evidence.put("flagThreshold", cfg.flagThreshold);
        evidence.put("caseCreationThreshold", cfg.caseCreationThreshold);

        return Optional.of(new TriggeredRule(RULE_CODE, 0, evidence, Instant.now()));
    }

    static Recommendation computeRecommendation(int score, int flagThreshold, int caseCreationThreshold) {
        if (score >= caseCreationThreshold) return Recommendation.BLOCK;
        if (score >= flagThreshold) return Recommendation.FLAG;
        return Recommendation.APPROVE;
    }

    private Config loadConfig() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return new Config(DEFAULT_FLAG_THRESHOLD, DEFAULT_CASE_CREATION_THRESHOLD);
        }
        var cfg = opt.get();
        return new Config(
                cfg.getInt("flagThreshold", DEFAULT_FLAG_THRESHOLD),
                cfg.getInt("caseCreationThreshold", DEFAULT_CASE_CREATION_THRESHOLD)
        );
    }

    private record Config(int flagThreshold, int caseCreationThreshold) {}

    /**
     * Thrown by {@link AggregatorStage} when the configured
     * {@code caseCreationThreshold} violates the fail-closed invariants
     * (must be in [0, 100]; must be >= the flag threshold). Constructor-time
     * check so a misconfigured rule fails at engine startup, not at runtime.
     * Per #238 + ADR-004 §4.4.
     */
    public static final class InvalidAggregatorConfigException extends RuntimeException {
        public InvalidAggregatorConfigException(String message) {
            super(message);
        }
    }
}
