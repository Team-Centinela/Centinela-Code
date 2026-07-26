package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStats;
import com.centinela.serverless.domain.port.TransactionStatsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AtypicalAmountRule implements PipelineStage {

    private static final String RULE_CODE = "FR-2";
    static final double DEFAULT_ZSCORE_THRESHOLD = 2.5;
    static final int DEFAULT_SCORE = 25;

    private final TransactionStatsRepository statsRepo;
    private final RuleConfigRepository configRepo;

    public AtypicalAmountRule(TransactionStatsRepository statsRepo, RuleConfigRepository configRepo) {
        this.statsRepo = statsRepo;
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        var statsOpt = statsRepo.findByAccountId(ctx.sourceTransaction().accountId());
        if (statsOpt.isEmpty()) {
            return Optional.empty();
        }
        TransactionStats stats = statsOpt.get();
        if (stats.stdDev().compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal amount = ctx.sourceTransaction().amount();
        double zScore = amount.subtract(stats.avg())
                .divide(stats.stdDev(), 10, RoundingMode.HALF_UP)
                .abs()
                .doubleValue();

        var cfg = loadConfig();
        if (zScore > cfg.zScoreThreshold) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("amount", amount.doubleValue());
            evidence.put("historicalAvg", stats.avg().doubleValue());
            evidence.put("historicalStdDev", stats.stdDev().doubleValue());
            evidence.put("zScore", BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP).doubleValue());
            evidence.put("threshold", cfg.zScoreThreshold);
            return Optional.of(new TriggeredRule(RULE_CODE, cfg.score, evidence, Instant.now()));
        }
        return Optional.empty();
    }

    private Config loadConfig() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return new Config(DEFAULT_ZSCORE_THRESHOLD, DEFAULT_SCORE);
        }
        var cfg = opt.get();
        return new Config(
                cfg.get("zScoreThreshold", DEFAULT_ZSCORE_THRESHOLD),
                cfg.get("score", DEFAULT_SCORE)
        );
    }

    private record Config(double zScoreThreshold, int score) {}
}
