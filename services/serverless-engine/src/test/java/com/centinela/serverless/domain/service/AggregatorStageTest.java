package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AggregatorStageTest {

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-agg", new BigDecimal("100.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    /** ADR-004 §4.4 canonical threshold (single source of truth) + soft FLAG floor. */
    private static final int SCORE_THRESHOLD = 70;
    private static final int FLAG_THRESHOLD = 30;

    private static AggregatorStage newAggregator() {
        return new AggregatorStage(SCORE_THRESHOLD, FLAG_THRESHOLD);
    }

    @Test
    void shouldReturnSyntheticRuleWithScoreZero() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of("k", "v"), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("AGGREGATOR", result.get().ruleCode());
        assertEquals(0, result.get().score(), "synthetic rule must have score 0 to avoid double-counting");
    }

    @Test
    void shouldClampScoreTo100() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 60, Map.of(), Instant.now()));
        ctx.addTriggeredRule(new TriggeredRule("FR-4", 60, Map.of(), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(100, result.get().rawEvidence().get("totalScore"));
    }

    @Test
    void recommendationShouldBeApproveForScoreBelowFlag() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of(), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("APPROVE", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void recommendationShouldBeFlagForScoreBetweenFlagAndScore() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 50, Map.of(), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("FLAG", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void recommendationShouldBeBlockAtScoreThreshold() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 70, Map.of(), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("BLOCK", result.get().rawEvidence().get("recommendation"));
    }

    /**
     * Regression test for SrLampi1001 review on PR #268 (finding 1 action 2):
     * with the single ADR-004 §4.4 threshold (70), a transaction whose three
     * stages sum to 80 must produce BLOCK — not FLAG under a misconfigured
     * split (50/70 was the previous bug).
     */
    @Test
    void probe30_25_25ShouldProduceBlockNotFlag() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 30, Map.of(), Instant.now()));
        ctx.addTriggeredRule(new TriggeredRule("FR-2", 25, Map.of(), Instant.now()));
        ctx.addTriggeredRule(new TriggeredRule("FR-4", 25, Map.of(), Instant.now()));
        var stage = newAggregator();

        var result = stage.evaluate(ctx);

        // SrLampi1001 probe: total = 80, recommendation must be BLOCK.
        // After PR #271 review concurrency fix, the recommendation travels on
        // the returned TriggeredRule's rawEvidence (not a singleton field)
        // so the read is thread-safe.
        assertEquals(Recommendation.BLOCK, Recommendation.valueOf((String) result.get().rawEvidence().get("recommendation")),
                "30 + 25 + 25 = 80 must produce BLOCK under ADR-004 §4.4 (single scoreThreshold=70)");
        assertEquals(80, ctx.accumulatedScore());
    }

    @Test
    void shouldUseCustomFlagThreshold() {
        // Custom flagThreshold=10 (still < scoreThreshold=70). Aggregator with
        // score 20 must produce FLAG because 20 >= 10.
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of(), Instant.now()));
        var stage = new AggregatorStage(70, 10);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("FLAG", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void shouldUseCustomScoreThreshold() {
        // Custom scoreThreshold=50 (still > flagThreshold=30). Aggregator with
        // score 50 must produce BLOCK.
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 50, Map.of(), Instant.now()));
        var stage = new AggregatorStage(50, 30);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("BLOCK", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void rawEvidenceContainsAllFields() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 40, Map.of(), Instant.now()));
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        assertEquals(40, evidence.get("totalScore"));
        assertEquals("FLAG", evidence.get("recommendation"));
        assertEquals(1, evidence.get("rulesTriggered"));
        // ADR-004 §4.4: single threshold key, no more blockThreshold.
        assertEquals(30, evidence.get("flagThreshold"));
        assertEquals(70, evidence.get("scoreThreshold"));
    }

    @Test
    void computeRecommendationMapsCorrectly() {
        assertEquals(Recommendation.APPROVE, AggregatorStage.computeRecommendation(0, 30, 70));
        assertEquals(Recommendation.APPROVE, AggregatorStage.computeRecommendation(29, 30, 70));
        assertEquals(Recommendation.FLAG, AggregatorStage.computeRecommendation(30, 30, 70));
        assertEquals(Recommendation.FLAG, AggregatorStage.computeRecommendation(69, 30, 70));
        assertEquals(Recommendation.BLOCK, AggregatorStage.computeRecommendation(70, 30, 70));
        assertEquals(Recommendation.BLOCK, AggregatorStage.computeRecommendation(100, 30, 70));
    }

    @Test
    void zeroTriggeredRulesShouldStillProduceDecision() {
        var ctx = new EvaluationContext(tx);
        var stage = newAggregator();

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().rawEvidence().get("totalScore"));
        assertEquals("APPROVE", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void constructorFailsClosedWhenScoreThresholdBelowZero() {
        assertThrows(AggregatorStage.InvalidAggregatorConfigException.class,
                () -> new AggregatorStage(-1, 30));
    }

    @Test
    void constructorFailsClosedWhenScoreThresholdAboveHundred() {
        assertThrows(AggregatorStage.InvalidAggregatorConfigException.class,
                () -> new AggregatorStage(101, 30));
    }

    @Test
    void constructorFailsClosedWhenFlagThresholdBelowZero() {
        assertThrows(AggregatorStage.InvalidAggregatorConfigException.class,
                () -> new AggregatorStage(70, -1));
    }

    /**
     * SrLampi1001 review on PR #268 (finding 1 action 3): strict inequality
     * {@code flagThreshold < scoreThreshold} must be enforced — equality
     * would collapse FLAG and BLOCK into the same recommendation.
     */
    @Test
    void constructorFailsClosedWhenFlagThresholdEqualsScoreThreshold() {
        var ex = assertThrows(AggregatorStage.InvalidAggregatorConfigException.class,
                () -> new AggregatorStage(70, 70));
        assertTrue(ex.getMessage().contains("must be < scoreThreshold"),
                "message must call out the strict inequality: " + ex.getMessage());
    }

    @Test
    void constructorFailsClosedWhenFlagThresholdExceedsScoreThreshold() {
        var ex = assertThrows(AggregatorStage.InvalidAggregatorConfigException.class,
                () -> new AggregatorStage(70, 80));
        assertTrue(ex.getMessage().contains("must be < scoreThreshold"));
    }
}
