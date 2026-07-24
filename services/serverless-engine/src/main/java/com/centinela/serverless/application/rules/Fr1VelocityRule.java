package com.centinela.serverless.application.rules;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FR-1 Velocity rule — counts transactions in a sliding window for {@code accountId}.
 *
 * <p>owner: @3105jero (logic placeholder). This implementation is the wallet of the
 * contract that #54 lists. The Scoring value ({@link #score(long, long)}) is the team's
 * call; the rule here is intentionally thin and lets the data layer drive the verdict.
 */
@Component
public class Fr1VelocityRule implements RuleStage {

    private final RuleEvidenceRepository repository;
    private final long windowSeconds;
    private final int velocityThreshold;

    public Fr1VelocityRule(RuleEvidenceRepository repository,
                           @org.springframework.beans.factory.annotation.Value("${fraud.rule.fr1.window-seconds:600}") long windowSeconds,
                           @org.springframework.beans.factory.annotation.Value("${fraud.rule.fr1.velocity-threshold:5}") int velocityThreshold) {
        this.repository = repository;
        this.windowSeconds = windowSeconds;
        this.velocityThreshold = velocityThreshold;
    }

    @Override
    public String ruleCode() {
        return "FR-1";
    }

    @Override
    public TriggeredRule evaluate(TransactionMessage tx) {
        long count = repository.countRecentForAccount(tx);
        if (count < velocityThreshold) {
            return TriggeredRule.notFired(ruleCode());
        }
        double score = score(count, velocityThreshold);
        Map<String, Object> evidence = Map.of(
                "windowSeconds", windowSeconds,
                "recentTxCount", count,
                "threshold", velocityThreshold
        );
        return TriggeredRule.fired(ruleCode(), score, evidence);
    }

    static double score(long count, long threshold) {
        double over = (count - threshold) / (double) threshold;
        return Math.min(100.0d, 30.0d + over * 30.0d);
    }
}
