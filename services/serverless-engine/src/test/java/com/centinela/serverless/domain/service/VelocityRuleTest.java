package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import com.centinela.serverless.domain.port.TransactionStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VelocityRuleTest {

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-velocity-1", new BigDecimal("50.00"), "USD",
            "merchant-1", null, null, Instant.now()
    );

    @Test
    void shouldNotTriggerWhenCountBelowThreshold() {
        var txRepo = new StubTxRepo(9);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "should NOT trigger when count (9) <= threshold (10)");
    }

    @Test
    void shouldTriggerWhenCountExceedsThreshold() {
        var txRepo = new StubTxRepo(11);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(), "should trigger when count (11) > threshold (10)");
        TriggeredRule tr = result.get();
        assertEquals("FR-1", tr.ruleCode());
        assertEquals(VelocityRule.DEFAULT_SCORE, tr.score());
    }

    @Test
    void shouldNotTriggerWhenCountEqualsThreshold() {
        var txRepo = new StubTxRepo(10);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "should NOT trigger when count (10) == threshold (10)");
    }

    @Test
    void shouldNotTriggerWhenZeroTransactions() {
        var txRepo = new StubTxRepo(0);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseDefaultConfigWhenRepoReturnsEmpty() {
        var txRepo = new StubTxRepo(11);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        TriggeredRule tr = result.get();
        assertEquals(20, tr.score());
        // ADR-004 §4.2: window is exposed in seconds (windowMinutes * 60 = 300 here).
        assertEquals(300L, tr.rawEvidence().get("window_seconds"));
        assertEquals(10, tr.rawEvidence().get("threshold"));
        assertEquals(11, tr.rawEvidence().get("txn_count"));
        assertEquals(20, tr.rawEvidence().get("current_score_added"));
    }

    @Test
    void shouldApplyConfigFromRepository() {
        var txRepo = new StubTxRepo(11);
        var cfg = new RuleConfig("FR-1", true, Map.of(
                "windowMinutes", 10,
                "maxTxPerWindow", 5,
                "score", 35
        ));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        TriggeredRule tr = result.get();
        assertEquals(35, tr.score());
        assertEquals(600L, tr.rawEvidence().get("window_seconds"));  // 10 min * 60
        assertEquals(5, tr.rawEvidence().get("threshold"));
        assertEquals(35, tr.rawEvidence().get("current_score_added"));
    }

    @Test
    void shouldCountTransactionsInCorrectWindow() {
        Instant now = Instant.now();
        var txWithTimestamp = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-window", new BigDecimal("50.00"), "USD",
                "merchant-1", null, null, now
        );
        var txRepo = new StubTxRepo(3);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(txWithTimestamp);

        rule.evaluate(ctx);

        Instant expectedSince = now.minus(5, ChronoUnit.MINUTES);
        assertEquals(expectedSince, txRepo.lastSince);
    }

    @Test
    void rawEvidenceContainsCorrectFields() {
        var txRepo = new StubTxRepo(15);
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        // ADR-004 §4.2: only the pinned key set may appear (plus extra keys).
        assertTrue(evidence.containsKey("window_seconds"));
        assertTrue(evidence.containsKey("txn_count"));
        assertTrue(evidence.containsKey("threshold"));
        assertTrue(evidence.containsKey("current_score_added"));
    }

    @Test
    void shouldUseDefaultsWhenRuleIsDisabled() {
        var txRepo = new StubTxRepo(11);
        var cfg = new RuleConfig("FR-1", false, Map.of("windowMinutes", 99));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new VelocityRule(txRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        TriggeredRule tr = result.get();
        // Disabled rule -> compile-time defaults. Window exposed in seconds.
        assertEquals(Long.valueOf(VelocityRule.DEFAULT_WINDOW_MINUTES * 60L),
                tr.rawEvidence().get("window_seconds"));
        assertEquals(VelocityRule.DEFAULT_SCORE, tr.score());
    }

    private static class StubTxRepo implements TransactionStatisticsRepository {
        final long count;
        Instant lastSince;

        StubTxRepo(long count) {
            this.count = count;
        }

        @Override
        public long countByAccountIdSince(String accountId, Instant since) {
            this.lastSince = since;
            return count;
        }
    }

    private record StubConfigRepo(Optional<RuleConfig> config) implements RuleConfigRepository {
        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return config;
        }
    }
}
