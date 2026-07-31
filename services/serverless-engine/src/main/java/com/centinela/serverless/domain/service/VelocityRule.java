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
            // ADR-004 §4.2: FR-1 rawEvidence schema is pinned to
            //   window_seconds, txn_count, threshold, current_score_added.
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("window_seconds", cfg.windowMinutes * 60L);    // window stored in minutes in the rule config; expose as seconds per ADR-004.
            evidence.put("txn_count", (int) count);
            evidence.put("threshold", cfg.maxTxPerWindow);
            evidence.put("current_score_added", cfg.score);
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
                cfg.getInt("windowMinutes", DEFAULT_WINDOW_MINUTES),
                cfg.getInt("maxTxPerWindow", DEFAULT_MAX_TX_PER_WINDOW),
                cfg.getInt("score", DEFAULT_SCORE)
        );
    }

    private record RuleConfigSnapshot(int windowMinutes, int maxTxPerWindow, int score) {}
}
