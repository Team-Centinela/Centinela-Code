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
        // rule's `current_score_added`). Amount fields are BigDecimal after
        // the §0.2.2 precision fix (#231); the explainer renders them with
        // their full scale rather than via double rounding.
        assertEquals(new BigDecimal("200.00"), evidence.get("current_amount_usd"));
        assertEquals(new BigDecimal("100.00"), evidence.get("historical_avg_usd"));
        assertEquals(new BigDecimal("20.00"), evidence.get("std_dev_usd"));
        assertEquals(50L, evidence.get("historical_sample_size"));
        assertEquals(0, new BigDecimal("5.0")
                .compareTo((BigDecimal) evidence.get("z_score")),
                "z_score must be BigDecimal 5.0 (scale 10) after precision fix");
        assertEquals(25, evidence.get("current_score_added"));
    }

    @Test
    void shouldNotTriggerWhenSampleSizeBelowMinSampleSize() {
        // #232: a freshly-onboarded account with too few historical transactions
        // must not trip FR-2. With stats.sampleSize=3 and default minSampleSize=10,
        // the rule silently returns empty even though the z-score would otherwise
        // fire (5.0 > 2.5).
        var smallStats = new TransactionStats(
                new BigDecimal("100.00"), new BigDecimal("20.00"), 3L
        );
        var statsRepo = new StubStatsRepo(Optional.of(smallStats));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(),
                "stats.sampleSize=3 < default minSampleSize=10 must NOT trigger FR-2");
    }

    @Test
    void shouldTriggerWhenSampleSizeMeetsMinSampleSizeOverride() {
        var cfg = new RuleConfig("FR-2", true, Map.of(
                "minSampleSize", 2,
                "zScoreThreshold", 2.5
        ));
        var smallStats = new TransactionStats(
                new BigDecimal("100.00"), new BigDecimal("20.00"), 3L
        );
        var statsRepo = new StubStatsRepo(Optional.of(smallStats));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(TX);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(),
                "sampleSize=3 >= override minSampleSize=2 must trigger (z=5.0 > 2.5)");
    }

    @Test
    void zScoreIsComputedInBigDecimalPrecisionNoDoubleRounding() {
        // Regression fixture for #231: with avg=99.99, stdDev=0.01, amount=100.00
        // a double-precision path produces z_score ~= 1.0 (or NaN-like due to
        // catastrophic cancellation). BigDecimal MathContext.DECIMAL64 keeps
        // the result exact at 1.00.
        var preciseStats = new TransactionStats(
                new BigDecimal("99.99"), new BigDecimal("0.01"), 200L
        );
        var preciseTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-bd", new BigDecimal("100.00"), "USD",
                "merchant-1", null, null, Instant.now()
        );
        var statsRepo = new StubStatsRepo(Optional.of(preciseStats));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new AtypicalAmountRule(statsRepo, cfgRepo);
        var ctx = new EvaluationContext(preciseTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        // z-score = (100.00 - 99.99) / 0.01 = 1.00; below threshold 2.5, so no trigger.
        assertTrue(result.isEmpty(),
                "z_score 1.00 must not trigger (below 2.5); BigDecimal keeps it exact");
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
        assertEquals(0, new BigDecimal("5.0")
                .compareTo((BigDecimal) result.get().rawEvidence().get("z_score")),
                "z_score must be BigDecimal 5.0 after precision fix");
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
