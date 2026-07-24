package com.centinela.serverless.application.rules;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FR-4 High-Risk Merchant — lookup in {@code flagged_merchants}.
 *
 * <p>Owner: @3105jero (logic placeholder). This is a stateless lookup; the data layer
 * decides whether a {@code merchantId} is flagged.
 */
@Component
public class Fr4HighRiskMerchantRule implements RuleStage {

    private final RuleEvidenceRepository repository;
    private final double hardFlagScore;

    public Fr4HighRiskMerchantRule(RuleEvidenceRepository repository,
                                   @org.springframework.beans.factory.annotation.Value("${fraud.rule.fr4.hard-flag-score:80.0}") double hardFlagScore) {
        this.repository = repository;
        this.hardFlagScore = hardFlagScore;
    }

    @Override
    public String ruleCode() {
        return "FR-4";
    }

    @Override
    public TriggeredRule evaluate(TransactionMessage tx) {
        if (tx.merchantId() == null || tx.merchantId().isBlank()) {
            return TriggeredRule.notFired(ruleCode());
        }
        boolean flagged = repository.isHighRiskMerchant(tx.merchantId());
        if (!flagged) {
            return TriggeredRule.notFired(ruleCode());
        }
        Map<String, Object> evidence = Map.of(
                "merchantId", tx.merchantId(),
                "source", "flagged_merchants"
        );
        return TriggeredRule.fired(ruleCode(), hardFlagScore, evidence);
    }
}
