package com.centinela.serverless.application;

import com.centinela.serverless.domain.model.FraudScore;
import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.application.rules.RuleStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FraudEvaluationPipeline {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationPipeline.class);

    private final List<RuleStage> stage1;
    private final List<RuleStage> stage2;
    private final double scoreThreshold;
    private final double scoreMax;
    private final MeterRegistry meterRegistry;

    private final Timer totalTimer;
    private final Timer stage1Timer;
    private final Timer stage2Timer;

    public FraudEvaluationPipeline(List<RuleStage> stages,
                                   @Value("${fraud.rule.score-threshold:60.0}") double scoreThreshold,
                                   @Value("${fraud.rule.score-max:100.0}") double scoreMax,
                                   MeterRegistry meterRegistry) {
        this.scoreThreshold = scoreThreshold;
        this.scoreMax = scoreMax;
        this.meterRegistry = meterRegistry;
        this.stage1 = stages.stream()
                .filter(s -> s.ruleCode().equals("FR-1") || s.ruleCode().equals("FR-4"))
                .toList();
        this.stage2 = stages.stream()
                .filter(s -> s.ruleCode().equals("FR-2") || s.ruleCode().equals("FR-3"))
                .toList();
        this.totalTimer = registerTimer("total");
        this.stage1Timer = registerTimer("stage1");
        this.stage2Timer = registerTimer("stage2");
    }

    private Timer registerTimer(String stage) {
        return Timer.builder("centinela.evaluation.duration_ms")
                .description("Rule pipeline latency per scope")
                .tag("stage", stage)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public FraudScore evaluate(TransactionMessage tx) {
        long startNanos = System.nanoTime();
        log.debug("Evaluating fraud pipeline for transaction {}", tx.transactionId());

        List<TriggeredRule> triggered = new ArrayList<>();
        double runningScore = 0.0d;

        long stage1Start = System.nanoTime();
        for (RuleStage stage : stage1) {
            TriggeredRule result = stage.evaluate(tx);
            recordResult(result);
            triggered.add(result);
            if (result.isFired()) {
                runningScore += result.score();
                if (runningScore >= scoreThreshold) {
                    FraudScore clamped = buildScore(tx, triggered, runningScore);
                    stage1Timer.record(System.nanoTime() - stage1Start, TimeUnit.NANOSECONDS);
                    totalTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                    return clamped;
                }
            }
        }
        stage1Timer.record(System.nanoTime() - stage1Start, TimeUnit.NANOSECONDS);

        long stage2Start = System.nanoTime();
        for (RuleStage stage : stage2) {
            TriggeredRule result = stage.evaluate(tx);
            recordResult(result);
            triggered.add(result);
            if (result.isFired()) {
                runningScore += result.score();
                if (runningScore >= scoreThreshold) {
                    FraudScore clamped = buildScore(tx, triggered, runningScore);
                    stage2Timer.record(System.nanoTime() - stage2Start, TimeUnit.NANOSECONDS);
                    totalTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                    return clamped;
                }
            }
        }
        stage2Timer.record(System.nanoTime() - stage2Start, TimeUnit.NANOSECONDS);
        totalTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        return buildScore(tx, triggered, runningScore);
    }

    private FraudScore buildScore(TransactionMessage tx, List<TriggeredRule> triggered, double runningScore) {
        boolean flagged = runningScore >= scoreThreshold;
        FraudScore raw = new FraudScore(tx.transactionId(), runningScore, flagged, triggered,
                "Score " + runningScore + "/" + scoreThreshold + "; rules fired: " + triggered.stream()
                        .filter(TriggeredRule::isFired)
                        .map(TriggeredRule::ruleCode)
                        .toList());
        return raw.withClampedScore(scoreMax);
    }

    private void recordResult(TriggeredRule result) {
        Counter.builder("centinela.rule.triggered")
                .description("Per-rule trigger counts")
                .tag("rule_code", result.ruleCode())
                .tag("result", result.isFired() ? "fired" : "not_fired")
                .register(meterRegistry)
                .increment();
    }
}
