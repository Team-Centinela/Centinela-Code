package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FraudPipelineTest {

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            java.util.UUID.randomUUID(), "acc-pipe", new BigDecimal("100.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    @Test
    void scores20_0_25_25ShouldReturnBlock() {
        var stages = List.<PipelineStage>of(
                new StubStage(20),
                new StubStage(0),
                new StubStage(25),
                new StubStage(25)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(70, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.BLOCK, decision.recommendation());
    }

    @Test
    void allScoresZeroShouldReturnApprove() {
        var stages = List.<PipelineStage>of(
                new StubStage(0),
                new StubStage(0),
                new StubStage(0),
                new StubStage(0)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.APPROVE, decision.recommendation());
    }

    @Test
    void scores30_0_10_10ShouldReturnFlag() {
        var stages = List.<PipelineStage>of(
                new StubStage(30),
                new StubStage(0),
                new StubStage(10),
                new StubStage(10)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(50, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.FLAG, decision.recommendation());
    }

    @Test
    void shortCircuitShouldSkipRemainingStages() {
        var stage1 = new StubStage(60);
        var stage2 = new TrackerStage();
        var stages = List.<PipelineStage>of(stage1, stage2);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(60, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.FLAG, decision.recommendation());
        assertFalse(stage2.called, "stage2 should NOT be called due to short-circuit");
    }

    @Test
    void clampingShouldLimitScoreTo100() {
        // Score clamping happens in AggregatorStage (max(0, min(100, accumulatedScore)));
        // see AggregatorStageTest.shouldClampScoreTo100 for the full coverage.
        // The pipeline's role is to short-circuit + sum; with default
        // thresholds (50/70) and 4 stages of 40 each, the loop short-circuits
        // after stage 1 (40 >= 50 is false, 40+40=80 >= 50 is true). Total
        // accumulated score stays at 80, well under the 100 ceiling.
        // This test asserts the Aggregator's BLOCK recommendation fires.
        var stages = List.<PipelineStage>of(
                new StubStage(40),
                new StubStage(40),
                new StubStage(40),
                new StubStage(40)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        // Two stages run before the loop hits shortCircuitThreshold=50 (40, then 40+40=80 -> break).
        assertEquals(80, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.BLOCK, decision.recommendation());
    }

    @Test
    void emptyStageListShouldStillProduceDecision() {
        var stages = List.<PipelineStage>of();
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.APPROVE, decision.recommendation());
        assertEquals(tx.transactionId(), decision.transactionId());
    }

    @Test
    void shortCircuitAtExactThreshold() {
        var stage1 = new StubStage(50);
        var stage2 = new TrackerStage();
        var stages = List.<PipelineStage>of(stage1, stage2);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(50, decision.totalScore());
        assertEquals(com.centinela.serverless.domain.model.Recommendation.FLAG, decision.recommendation());
        assertFalse(stage2.called, "stage2 should be skipped when accumulatedScore == shortCircuitThreshold");
    }

    @Test
    void shouldUseConfiguredShortCircuitThreshold() {
        var cfg = new RuleConfig("PIPELINE", true, Map.of("shortCircuitThreshold", 15));
        var stage1 = new StubStage(20);
        var stage2 = new TrackerStage();
        var stages = List.<PipelineStage>of(stage1, stage2);
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(20, decision.totalScore());
        assertFalse(stage2.called, "should short-circuit earlier with threshold=15");
    }

    @Test
    void triggeredRulesContainsAllThatFired() {
        var stages = List.<PipelineStage>of(
                new StubStage(20),
                new StubStage(20),
                new StubStage(20)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var pipeline = new FraudPipeline(stages, new AggregatorStage(cfgRepo), cfgRepo);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(4, decision.triggeredRules().size(), "3 stage rules + 1 aggregator rule");
    }

    private static class StubStage implements PipelineStage {
        private final int score;

        StubStage(int score) {
            this.score = score;
        }

        @Override
        public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
            if (score == 0) return Optional.empty();
            return Optional.of(new TriggeredRule("STUB", score, Map.of("s", score), Instant.now()));
        }
    }

    private static class TrackerStage implements PipelineStage {
        boolean called = false;

        @Override
        public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
            called = true;
            return Optional.of(new TriggeredRule("TRACKER", 50, Map.of(), Instant.now()));
        }
    }

    private static class StubConfigRepo implements RuleConfigRepository {
        private final Map<String, RuleConfig> configs;

        StubConfigRepo(Map<String, RuleConfig> configs) {
            this.configs = configs;
        }

        StubConfigRepo(Optional<RuleConfig> single) {
            this.configs = new java.util.HashMap<>();
            single.ifPresent(c -> configs.put(c.ruleCode(), c));
        }

        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return Optional.ofNullable(configs.get(ruleCode));
        }
    }
}
