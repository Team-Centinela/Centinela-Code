package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStats;
import com.centinela.serverless.domain.port.TransactionStatsRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AtypicalAmountRule implements PipelineStage {

    private static final String RULE_CODE = "FR-2";
    static final double DEFAULT_ZSCORE_THRESHOLD = 2.5;
    static final int DEFAULT_SCORE = 25;

    /**
     * ADR-004 §4.2: the z-score carried in rawEvidence and compared to the
     * threshold is computed and stored as {@link BigDecimal}. We round to
     * {@code ZSCORE_SCALE} decimal places so the value fits in JSON
     * without scientific notation and matches the explainer template
     * {@code z_score} precision. {@link MathContext#DECIMAL64} keeps the
     * division stable for values larger than ~1e9.
     */
    static final int ZSCORE_SCALE = 10;

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
        BigDecimal zScore = amount.subtract(stats.avg())
                .divide(stats.stdDev(), MathContext.DECIMAL64)
                .abs()
                .setScale(ZSCORE_SCALE, RoundingMode.HALF_UP);

        var cfg = loadConfig();
        BigDecimal threshold = BigDecimal.valueOf(cfg.zScoreThreshold);
        if (zScore.compareTo(threshold) > 0) {
            // ADR-004 §4.2: FR-2 rawEvidence schema is pinned to
            //   historical_avg_usd, historical_sample_size, current_amount_usd,
            //   std_dev_usd, z_score, current_score_added.
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("current_amount_usd", amount);
            evidence.put("historical_avg_usd", stats.avg());
            evidence.put("std_dev_usd", stats.stdDev());
            evidence.put("historical_sample_size", stats.sampleSize());
            // ADR-004 §4.2 says z_score is a JSON number; we keep BigDecimal
            // as the canonical payload type so the explainer can render it
            // without precision loss (USE_BIG_DECIMAL_FOR_FLOATS handles
            // JSON encoding; see EngineObjectMapperConfig).
            evidence.put("z_score", zScore);
            evidence.put("current_score_added", cfg.score);
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
                cfg.getDouble("zScoreThreshold", DEFAULT_ZSCORE_THRESHOLD),
                cfg.getInt("score", DEFAULT_SCORE)
        );
    }

    private record Config(double zScoreThreshold, int score) {}
}
