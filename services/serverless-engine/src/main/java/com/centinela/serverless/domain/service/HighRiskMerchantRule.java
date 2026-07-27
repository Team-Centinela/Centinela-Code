package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.FlaggedMerchantRepository;
import com.centinela.serverless.domain.port.RuleConfigRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class HighRiskMerchantRule implements PipelineStage {

    private static final String RULE_CODE = "FR-4";
    static final int DEFAULT_SCORE = 30;

    private final FlaggedMerchantRepository flaggedRepo;
    private final RuleConfigRepository configRepo;

    public HighRiskMerchantRule(FlaggedMerchantRepository flaggedRepo, RuleConfigRepository configRepo) {
        this.flaggedRepo = flaggedRepo;
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        String merchantId = ctx.sourceTransaction().merchantId();
        if (merchantId == null || merchantId.isBlank()) {
            return Optional.empty();
        }

        var flagged = flaggedRepo.findByMerchantId(merchantId);
        if (flagged.isPresent()) {
            int score = loadScore();
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("merchantId", merchantId);
            evidence.put("flagged", true);
            evidence.put("flaggedSince", flagged.get().flaggedAt().toString());
            return Optional.of(new TriggeredRule(RULE_CODE, score, evidence, Instant.now()));
        }
        return Optional.empty();
    }

    private int loadScore() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return DEFAULT_SCORE;
        }
        return opt.get().get("score", DEFAULT_SCORE);
    }
}
