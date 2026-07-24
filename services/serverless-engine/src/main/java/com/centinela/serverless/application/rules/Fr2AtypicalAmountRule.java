package com.centinela.serverless.application.rules;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FR-2 Atypical Amount — z-score against the historical mean for the account.
 *
 * <p>owner: @3105jero (logic placeholder). Returns 0 evidence if no history is present
 * (z-score undefined for n=0). Threshold and score weights come from configuration;
 * the rule itself does not own them.
 */
@Component
public class Fr2AtypicalAmountRule implements RuleStage {

    private final RuleEvidenceRepository repository;
    private final double zThreshold;

    public Fr2AtypicalAmountRule(RuleEvidenceRepository repository,
                                 @org.springframework.beans.factory.annotation.Value("${fraud.rule.fr2.z-threshold:2.5}") double zThreshold) {
        this.repository = repository;
        this.zThreshold = zThreshold;
    }

    @Override
    public String ruleCode() {
        return "FR-2";
    }

    @Override
    public TriggeredRule evaluate(TransactionMessage tx) {
        double avg = repository.getAverageAmount(tx.accountId());
        if (Double.isNaN(avg) || avg <= 0d) {
            return TriggeredRule.notFired(ruleCode());
        }
        double deviation = Math.abs(tx.amount().doubleValue() - avg) / avg;
        double z = deviation * 5.0d;
        if (z < zThreshold) {
            return TriggeredRule.notFired(ruleCode());
        }
        double score = Math.min(40.0d, (z - zThreshold) * 8.0d);
        Map<String, Object> evidence = Map.of(
                "zScore", z,
                "historicalAverage", avg,
                "txAmount", tx.amount().doubleValue(),
                "threshold", zThreshold
        );
        return TriggeredRule.fired(ruleCode(), score, evidence);
    }
}
