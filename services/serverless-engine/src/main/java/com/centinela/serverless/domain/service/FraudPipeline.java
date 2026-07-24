package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class FraudPipeline {

    static final int DEFAULT_SCORE_THRESHOLD = 50;
    static final String PIPELINE_CONFIG_CODE = "PIPELINE";

    private final List<PipelineStage> stages;
    private final AggregatorStage aggregator;
    private final RuleConfigRepository configRepo;

    public FraudPipeline(List<PipelineStage> stages, AggregatorStage aggregator, RuleConfigRepository configRepo) {
        this.stages = stages;
        this.aggregator = aggregator;
        this.configRepo = configRepo;
    }

    public FraudDecision execute(EvaluationContext ctx) {
        int scoreThreshold = loadScoreThreshold();

        for (PipelineStage stage : stages) {
            if (ctx.accumulatedScore() >= scoreThreshold) {
                break;
            }
            Optional<TriggeredRule> result = stage.evaluate(ctx);
            result.ifPresent(ctx::addTriggeredRule);
        }

        aggregator.evaluate(ctx).ifPresent(ctx::addTriggeredRule);

        int totalScore = Math.min(100, Math.max(0, ctx.accumulatedScore()));
        return FraudDecision.fromContext(
                ctx.sourceTransaction().transactionId(),
                ctx.triggeredRules(),
                Instant.now()
        );
    }

    private int loadScoreThreshold() {
        var opt = configRepo.findByRuleCode(PIPELINE_CONFIG_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return DEFAULT_SCORE_THRESHOLD;
        }
        return opt.get().get("scoreThreshold", DEFAULT_SCORE_THRESHOLD);
    }
}
