package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStats;
import com.centinela.serverless.domain.port.TransactionStatsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AtypicalAmountRuleTest {

    private static final TransactionStats STATS = new TransactionStats(
            new BigDecimal("100.00"), new BigDecimal("20.00")
    );
    private static final TransactionReceivedEvent TX = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-atypical", new BigDecimal("200.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    @Test
    void shouldTriggerWhenZScoreExceedsThreshold() {
        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(), "z-score 5.0 > 2.5 should trigger");
        assertEquals("FR-2", result.get().ruleCode());
    }

    @Test
    void shouldNotTriggerWhenZScoreBelowThreshold() {
        var closeTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-atypical", new BigDecimal("110.00"), "USD",
                "merchant-1", null, null, Instant.now()
        );
        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(closeTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "z-score 0.5 <= 2.5 should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenZScoreEqualsThreshold() {
        BigDecimal amount = new BigDecimal("150.00");
        var exactTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-atypical", amount, "USD",
                "merchant-1", null, null, Instant.now()
        );
        double zScore = amount.subtract(STATS.avg())
                .divide(STATS.stdDev(), 10, java.math.RoundingMode.HALF_UP)
                .abs().doubleValue();
        assert zScore == 2.5 : "precondition: z-score must equal 2.5";

        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(exactTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "z-score 2.5 == threshold 2.5 should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenNoStatsAvailable() {
        var statsRepo = new StubStatsRepo(Optional.empty());
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "no historical stats should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenStdDevIsZero() {
        var zeroStats = new TransactionStats(new BigDecimal("100.00"), BigDecimal.ZERO);
        var statsRepo = new StubStatsRepo(Optional.of(zeroStats));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "zero stddev should NOT trigger");
    }

    @Test
    void rawEvidenceContainsCorrectFields() {
        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        assertEquals(200.0, evidence.get("amount"));
        assertEquals(100.0, evidence.get("historicalAvg"));
        assertEquals(20.0, evidence.get("historicalStdDev"));
        assertEquals(5.0, evidence.get("zScore"));
        assertEquals(2.5, evidence.get("threshold"));
    }

    @Test
    void shouldUseConfigOverrides() {
        var cfg = new RuleConfig("FR-2", true, Map.of(
                "zScoreThreshold", 1.0,
                "score", 40
        ));
        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);

        var closeTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-atypical", new BigDecimal("125.00"), "USD",
                "merchant-1", null, null, Instant.now()
        );
        var ctx = new EvaluationContext(closeTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(), "z-score 1.25 > threshold 1.0 should trigger with config override");
        assertEquals(40, result.get().score());
    }

    @Test
    void shouldUseDefaultsWhenDisabled() {
        var cfg = new RuleConfig("FR-2", false, Map.of("zScoreThreshold", 0.1));
        var statsRepo = new StubStatsRepo(Optional.of(STATS));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(AtypicalAmountRule.DEFAULT_SCORE, result.get().score());
        assertEquals(AtypicalAmountRule.DEFAULT_ZSCORE_THRESHOLD,
                ((Number) result.get().rawEvidence().get("threshold")).doubleValue());
    }

    private record StubStatsRepo(Optional<TransactionStats> stats) implements TransactionStatsRepository {
        @Override
        public Optional<TransactionStats> findByAccountId(String accountId) {
            return stats;
        }
    }

    private record StubConfigRepo(Optional<RuleConfig> config) implements RuleConfigRepository {
        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return config;
        }
    }
}
