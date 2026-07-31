package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
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

    @Test
    void shouldReturnSyntheticRuleWithScoreZero() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of("k", "v"), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

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
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(100, result.get().rawEvidence().get("totalScore"));
    }

    @Test
    void recommendationShouldBeApproveForScoreBelow30() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("APPROVE", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void recommendationShouldBeFlagForScoreBetween30And69() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 50, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("FLAG", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void recommendationShouldBeBlockForScore70OrAbove() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 70, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("BLOCK", result.get().rawEvidence().get("recommendation"));
    }

    @Test
    void shouldUseConfiguredThresholds() {
        var cfg = new RuleConfig("AGGREGATOR", true, Map.of(
                "flagThreshold", 10,
                "caseCreationThreshold", 80
        ));
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("FLAG", result.get().rawEvidence().get("recommendation"),
                "score 20 >= flagThreshold 10 should be FLAG");
    }

    @Test
    void shouldUseDefaultsWhenDisabled() {
        var cfg = new RuleConfig("AGGREGATOR", false, Map.of("flagThreshold", 5));
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 20, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals("APPROVE", result.get().rawEvidence().get("recommendation"),
                "disabled rule uses default flagThreshold=30, score 20 < 30 => APPROVE");
    }

    @Test
    void rawEvidenceContainsAllFields() {
        var ctx = new EvaluationContext(tx);
        ctx.addTriggeredRule(new TriggeredRule("FR-1", 40, Map.of(), Instant.now()));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        assertEquals(40, evidence.get("totalScore"));
        assertEquals("FLAG", evidence.get("recommendation"));
        assertEquals(1, evidence.get("rulesTriggered"));
        assertEquals(30, evidence.get("flagThreshold"));
        // ADR-004 §4.4 + #237: renamed from 'blockThreshold' to 'caseCreationThreshold'
        // because the role is the case-creation gate, not a generic block threshold.
        assertEquals(70, evidence.get("caseCreationThreshold"));
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
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var stage = new AggregatorStage(cfgRepo);

        Optional<TriggeredRule> result = stage.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().rawEvidence().get("totalScore"));
        assertEquals("APPROVE", result.get().rawEvidence().get("recommendation"));
    }

    private record StubConfigRepo(Optional<RuleConfig> config) implements RuleConfigRepository {
        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return config;
        }
    }
}
