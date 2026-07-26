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
        assertEquals(5, tr.rawEvidence().get("windowMinutes"));
        assertEquals(10, tr.rawEvidence().get("threshold"));
        assertEquals(11, tr.rawEvidence().get("transactionCount"));
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
        assertEquals(10, tr.rawEvidence().get("windowMinutes"));
        assertEquals(5, tr.rawEvidence().get("threshold"));
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
        assertTrue(evidence.containsKey("windowMinutes"));
        assertTrue(evidence.containsKey("transactionCount"));
        assertTrue(evidence.containsKey("threshold"));
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
        assertEquals(VelocityRule.DEFAULT_WINDOW_MINUTES, tr.rawEvidence().get("windowMinutes"));
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
