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
    static final int DEFAULT_BLOCK_THRESHOLD = 70;

    private final RuleConfigRepository configRepo;

    public AggregatorStage(RuleConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        var cfg = loadConfig();

        int clampedScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        Recommendation rec = computeRecommendation(clampedScore, cfg.flagThreshold, cfg.blockThreshold);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalScore", clampedScore);
        evidence.put("recommendation", rec.name());
        evidence.put("rulesTriggered", ctx.triggeredRules().size());
        evidence.put("flagThreshold", cfg.flagThreshold);
        evidence.put("blockThreshold", cfg.blockThreshold);

        return Optional.of(new TriggeredRule(RULE_CODE, 0, evidence, Instant.now()));
    }

    static Recommendation computeRecommendation(int score, int flagThreshold, int blockThreshold) {
        if (score >= blockThreshold) return Recommendation.BLOCK;
        if (score >= flagThreshold) return Recommendation.FLAG;
        return Recommendation.APPROVE;
    }

    private Config loadConfig() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return new Config(DEFAULT_FLAG_THRESHOLD, DEFAULT_BLOCK_THRESHOLD);
        }
        var cfg = opt.get();
        return new Config(
                cfg.getInt("flagThreshold", DEFAULT_FLAG_THRESHOLD),
                cfg.getInt("blockThreshold", DEFAULT_BLOCK_THRESHOLD)
        );
    }

    private record Config(int flagThreshold, int blockThreshold) {}
}
