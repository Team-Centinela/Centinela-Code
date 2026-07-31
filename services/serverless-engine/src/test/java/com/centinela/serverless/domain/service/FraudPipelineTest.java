package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudPipelineTest {

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-pipe", new BigDecimal("100.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    /** ADR-004 §4.4 canonical threshold + soft FLAG floor. */
    private static final int SCORE_THRESHOLD = 70;
    private static final int FLAG_THRESHOLD = 30;

    private static FraudPipeline newPipeline(List<PipelineStage> stages) {
        return new FraudPipeline(stages, new AggregatorStage(SCORE_THRESHOLD, FLAG_THRESHOLD), SCORE_THRESHOLD);
    }

    @Test
    void scores20_0_25_25ShouldReturnBlock() {
        // 20 + 0 + 25 + 25 = 70 → loop short-circuits after stage 3.
        var stages = List.<PipelineStage>of(
                new StubStage(20),
                new StubStage(0),
                new StubStage(25),
                new StubStage(25)
        );
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(70, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
    }

    @Test
    void allScoresZeroShouldReturnApprove() {
        var stages = List.<PipelineStage>of(
                new StubStage(0),
                new StubStage(0),
                new StubStage(0),
                new StubStage(0)
        );
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(Recommendation.APPROVE, decision.recommendation());
    }

    @Test
    void scores30_0_10_10ShouldReturnFlag() {
        // 30 + 0 + 10 + 10 = 50 → loop runs all stages (50 < 70). Aggregator: 50 FLAG.
        var stages = List.<PipelineStage>of(
                new StubStage(30),
                new StubStage(0),
                new StubStage(10),
                new StubStage(10)
        );
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(50, decision.totalScore());
        assertEquals(Recommendation.FLAG, decision.recommendation());
    }

    /**
     * SrLampi1001 review on PR #268 (finding 1 action 2): under ADR-004 §4.4
     * single-threshold model, a probe with stages 30 + 25 + 25 (= 80) must
     * produce BLOCK. The previous §0.2.3 split (50/70) returned FLAG and
     * opened a false-negative window. This test pins the corrected behaviour.
     */
    @Test
    void probe30_25_25ShouldProduceBlockNotFlag() {
        var stages = List.<PipelineStage>of(
                new StubStage(30),
                new StubStage(25),
                new StubStage(25)
        );
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(80, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation(),
                "30+25+25 = 80 must produce BLOCK under ADR-004 §4.4 (single scoreThreshold=70)");
    }

    @Test
    void shortCircuitShouldSkipRemainingStages() {
        var stage1 = new StubStage(80);   // >= 70 → short-circuit
        var stage2 = new TrackerStage();
        var stages = List.<PipelineStage>of(stage1, stage2);
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(80, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
        assertFalse(stage2.called, "stage2 should NOT be called due to short-circuit at 70");
    }

    @Test
    void shortCircuitAtExactThreshold() {
        var stage1 = new StubStage(70);   // == 70 → short-circuit
        var stage2 = new TrackerStage();
        var stages = List.<PipelineStage>of(stage1, stage2);
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(70, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
        assertFalse(stage2.called, "stage2 should be skipped when accumulatedScore == scoreThreshold");
    }

    @Test
    void emptyStageListShouldStillProduceDecision() {
        var stages = List.<PipelineStage>of();
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(0, decision.totalScore());
        assertEquals(Recommendation.APPROVE, decision.recommendation());
        assertEquals(tx.transactionId(), decision.transactionId());
    }

    @Test
    void triggeredRulesContainsAllThatFired() {
        var stages = List.<PipelineStage>of(
                new StubStage(20),
                new StubStage(20),
                new StubStage(20)
        );
        var pipeline = newPipeline(stages);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(4, decision.triggeredRules().size(), "3 stage rules + 1 aggregator rule");
    }

    @Test
    void customScoreThresholdHonored() {
        var stages = List.<PipelineStage>of(
                new StubStage(40),
                new StubStage(40),
                new StubStage(40)
        );
        // Custom scoreThreshold=50 means pipeline breaks after stage 2 (40+40=80 >= 50).
        var pipeline = new FraudPipeline(stages, new AggregatorStage(50, 30), 50);
        var ctx = new EvaluationContext(tx);

        FraudDecision decision = pipeline.execute(ctx);

        assertEquals(80, decision.totalScore());
        assertEquals(Recommendation.BLOCK, decision.recommendation());
        assertEquals(50, decision.scoreThreshold(),
                "FraudDecision must record the actual scoreThreshold applied (SrLampi1001 finding 3)");
    }

    @Test
    void aggregatorAndFraudDecisionRecommendationMustAgree() {
        // SrLampi1001 finding 3: under any threshold + score combination, the
        // raw evidence recommendation and the FraudDecision.recommendation
        // MUST agree — no silent drift between the two decision paths.
        int[][] probes = {
                {70, 30, 80},  // totalScore, flagThreshold, scoreThreshold
                {70, 30, 70},
                {70, 30, 71},
                {50, 30, 60},
                {29, 30, 70},
                {0, 30, 70},
        };
        for (int[] probe : probes) {
            int totalScore = probe[0];
            int flag = probe[1];
            int score = probe[2];
            var aggregator = new AggregatorStage(score, flag);
            var pipeline = new FraudPipeline(List.<PipelineStage>of(), aggregator, score);
            var ctx = new EvaluationContext(tx);
            ctx.addTriggeredRule(new TriggeredRule("FR-X", totalScore, Map.of(), Instant.now()));

            FraudDecision decision = pipeline.execute(ctx);

            // Compare the aggregator's recommendation (from lastRecommendation)
            // with FraudDecision.recommendation. They MUST match.
            assertEquals(aggregator.lastRecommendation(), decision.recommendation(),
                    "FraudDecision.recommendation must match aggregator.lastRecommendation() at totalScore=" + totalScore
                            + ", flag=" + flag + ", score=" + score);
        }
    }

    @Test
    void constructorRejectsScoreThresholdBelowZero() {
        assertThrows(FraudPipeline.InvalidPipelineConfigException.class,
                () -> new FraudPipeline(List.of(), new AggregatorStage(70, 30), -1));
    }

    @Test
    void constructorRejectsScoreThresholdAboveHundred() {
        assertThrows(FraudPipeline.InvalidPipelineConfigException.class,
                () -> new FraudPipeline(List.of(), new AggregatorStage(70, 30), 101));
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
}
