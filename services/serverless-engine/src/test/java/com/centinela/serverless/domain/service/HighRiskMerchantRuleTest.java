package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.FlaggedMerchant;
import com.centinela.serverless.domain.port.FlaggedMerchantRepository;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HighRiskMerchantRuleTest {

    private static final FlaggedMerchant FLAGGED = new FlaggedMerchant(
            "merchant-fraud-1", "HIGH", Instant.parse("2026-01-15T10:00:00Z"), "Known fraudulent merchant"
    );

    private final TransactionReceivedEvent tx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-1", new BigDecimal("100.00"), "USD",
            "merchant-fraud-1", null, null, Instant.now()
    );

    @Test
    void shouldTriggerWhenMerchantIsFlagged() {
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(), "should trigger when merchant is flagged");
        assertEquals("FR-4", result.get().ruleCode());
        assertEquals(HighRiskMerchantRule.DEFAULT_SCORE, result.get().score());
    }

    @Test
    void shouldNotTriggerWhenMerchantIsNotFlagged() {
        var flaggedRepo = new StubFlaggedRepo(Optional.empty());
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "should NOT trigger when merchant is not flagged");
    }

    @Test
    void shouldNotTriggerWhenMerchantIdIsNull() {
        var txNoMerchant = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-1", new BigDecimal("100.00"), "USD",
                null, null, null, Instant.now()
        );
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(txNoMerchant);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "should NOT trigger when merchantId is null");
    }

    @Test
    void shouldNotTriggerWhenMerchantIdIsBlank() {
        var txBlankMerchant = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-1", new BigDecimal("100.00"), "USD",
                "   ", null, null, Instant.now()
        );
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(txBlankMerchant);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "should NOT trigger when merchantId is blank");
    }

    @Test
    void rawEvidenceContainsCorrectFields() {
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        assertEquals("merchant-fraud-1", evidence.get("merchantId"));
        assertEquals(true, evidence.get("flagged"));
        assertEquals("2026-01-15T10:00:00Z", evidence.get("flaggedSince"));
    }

    @Test
    void shouldUseScoreFromConfig() {
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfg = new RuleConfig("FR-4", true, Map.of("score", 45));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(45, result.get().score());
    }

    @Test
    void shouldUseDefaultsWhenConfigMissing() {
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(HighRiskMerchantRule.DEFAULT_SCORE, result.get().score());
    }

    @Test
    void shouldUseDefaultsWhenRuleIsDisabled() {
        var flaggedRepo = new StubFlaggedRepo(Optional.of(FLAGGED));
        var cfg = new RuleConfig("FR-4", false, Map.of("score", 50));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new HighRiskMerchantRule(flaggedRepo, cfgRepo);
        var ctx = new EvaluationContext(tx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        assertEquals(HighRiskMerchantRule.DEFAULT_SCORE, result.get().score());
    }

    private record StubFlaggedRepo(Optional<FlaggedMerchant> result) implements FlaggedMerchantRepository {
        @Override
        public Optional<FlaggedMerchant> findByMerchantId(String merchantId) {
            return result;
        }
    }

    private record StubConfigRepo(Optional<RuleConfig> config) implements RuleConfigRepository {
        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return config;
        }
    }
}
