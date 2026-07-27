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
            new BigDecimal("100.00"), new BigDecimal("20.00"), 50L
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
        var zeroStats = new TransactionStats(new BigDecimal("100.00"), BigDecimal.ZERO, 5L);
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
        // ADR-004 §4.2: only the pinned FR-2 keys may be present (plus the
        // rule's `current_score_added`).
        assertEquals(200.0, evidence.get("current_amount_usd"));
        assertEquals(100.0, evidence.get("historical_avg_usd"));
        assertEquals(20.0, evidence.get("std_dev_usd"));
        assertEquals(50L, evidence.get("historical_sample_size"));
        assertEquals(5.0, evidence.get("z_score"));
        assertEquals(25, evidence.get("current_score_added"));
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
        // ADR-004 §4.2 FR-2: the new schema has no separate `threshold` key.
        // z_score is the computed value for the actual tx, not the threshold.
        // Here amount=200, avg=100, stdDev=20 => z_score = |200-100|/20 = 5.0.
        assertEquals(5.0, ((Number) result.get().rawEvidence().get("z_score")).doubleValue(), 0.01);
        assertEquals(25, result.get().rawEvidence().get("current_score_added"));
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
