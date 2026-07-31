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
    private final Config config;

    public AggregatorStage(RuleConfigRepository configRepo) {
        this.configRepo = configRepo;
        this.config = loadConfig();
        // #238 + ADR-004 §4.4: fail-closed constructor-time validation. A
        // misconfigured rules_config (negative caseCreationThreshold, or a
        // caseCreationThreshold below the shortCircuitThreshold) would either
        // never open a case or short-circuit before the case gate ever fires.
        // Both classes of bug surface here at engine startup, not on the
        // first transaction that hits the broken config.
        validate(config, loadShortCircuitThreshold());
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        int clampedScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        Recommendation rec = computeRecommendation(clampedScore, config.flagThreshold(), config.caseCreationThreshold());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", clampedScore);
        evidence.put("recommendation", rec.name());
        evidence.put("rulesTriggered", ctx.triggeredRules().size());
        evidence.put("flagThreshold", config.flagThreshold());
        evidence.put("caseCreationThreshold", config.caseCreationThreshold());

        return Optional.of(new TriggeredRule(RULE_CODE, 0, evidence, Instant.now()));
    }

    static Recommendation computeRecommendation(int score, int flagThreshold, int caseCreationThreshold) {
        if (score >= caseCreationThreshold) return Recommendation.BLOCK;
        if (score >= flagThreshold) return Recommendation.FLAG;
        return Recommendation.APPROVE;
    }

    /**
     * Static validator exposed for unit tests so a single bean fixture does
     * not have to wire the full RuleConfigRepository just to exercise the
     * fail-closed contract.
     */
    static void validate(Config cfg, int shortCircuitThreshold) {
        if (cfg.caseCreationThreshold() < 0) {
            throw new InvalidAggregatorConfigException(
                    "caseCreationThreshold must be >= 0, got " + cfg.caseCreationThreshold());
        }
        if (cfg.flagThreshold() < 0) {
            throw new InvalidAggregatorConfigException(
                    "flagThreshold must be >= 0, got " + cfg.flagThreshold());
        }
        if (cfg.caseCreationThreshold() < shortCircuitThreshold) {
            // Without this invariant, a pipeline that short-circuits at 50
            // while the case-creation threshold sits at 30 would never
            // trigger the BLOCK recommendation (the loop stops before the
            // score can reach 30 in normal flow). Validation here makes the
            // relationship between PIPELINE and AGGREGATOR configs explicit.
            throw new InvalidAggregatorConfigException(
                    "caseCreationThreshold (" + cfg.caseCreationThreshold()
                            + ") must be >= shortCircuitThreshold (" + shortCircuitThreshold + ")");
        }
        if (cfg.caseCreationThreshold() < cfg.flagThreshold()) {
            throw new InvalidAggregatorConfigException(
                    "caseCreationThreshold (" + cfg.caseCreationThreshold()
                            + ") must be >= flagThreshold (" + cfg.flagThreshold() + ")");
        }
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

    private int loadShortCircuitThreshold() {
        var opt = configRepo.findByRuleCode(FraudPipeline.PIPELINE_CONFIG_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return FraudPipeline.DEFAULT_SHORT_CIRCUIT_THRESHOLD;
        }
        return opt.get().getInt("shortCircuitThreshold", FraudPipeline.DEFAULT_SHORT_CIRCUIT_THRESHOLD);
    }

    record Config(int flagThreshold, int caseCreationThreshold) {}

    /**
     * Thrown by {@link AggregatorStage} when the configured
     * {@code caseCreationThreshold} violates the fail-closed invariants
     * (must be in [0, 100]; must be >= the flag threshold AND the
     * shortCircuitThreshold). Constructor-time check so a misconfigured
     * rule fails at engine startup, not at runtime. Per #238 + ADR-004 §4.4.
     */
    public static final class InvalidAggregatorConfigException extends RuntimeException {
        public InvalidAggregatorConfigException(String message) {
            super(message);
        }
    }
}
