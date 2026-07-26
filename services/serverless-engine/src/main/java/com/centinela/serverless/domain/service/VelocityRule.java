package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStatisticsRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class VelocityRule implements PipelineStage {

    private static final String RULE_CODE = "FR-1";
    static final int DEFAULT_WINDOW_MINUTES = 5;
    static final int DEFAULT_MAX_TX_PER_WINDOW = 10;
    static final int DEFAULT_SCORE = 20;

    private final TransactionStatisticsRepository txRepo;
    private final RuleConfigRepository configRepo;

    public VelocityRule(TransactionStatisticsRepository txRepo, RuleConfigRepository configRepo) {
        this.txRepo = txRepo;
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        RuleConfigSnapshot cfg = loadConfig();

        Instant since = ctx.sourceTransaction().timestamp().minus(cfg.windowMinutes, ChronoUnit.MINUTES);
        long count = txRepo.countByAccountIdSince(ctx.sourceTransaction().accountId(), since);

        if (count > cfg.maxTxPerWindow) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("windowMinutes", cfg.windowMinutes);
            evidence.put("transactionCount", (int) count);
            evidence.put("threshold", cfg.maxTxPerWindow);
            return Optional.of(new TriggeredRule(RULE_CODE, cfg.score, evidence, Instant.now()));
        }
        return Optional.empty();
    }

    private RuleConfigSnapshot loadConfig() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return new RuleConfigSnapshot(DEFAULT_WINDOW_MINUTES, DEFAULT_MAX_TX_PER_WINDOW, DEFAULT_SCORE);
        }
        var cfg = opt.get();
        return new RuleConfigSnapshot(
                cfg.get("windowMinutes", DEFAULT_WINDOW_MINUTES),
                cfg.get("maxTxPerWindow", DEFAULT_MAX_TX_PER_WINDOW),
                cfg.get("score", DEFAULT_SCORE)
        );
    }

    private record RuleConfigSnapshot(int windowMinutes, int maxTxPerWindow, int score) {}
}
