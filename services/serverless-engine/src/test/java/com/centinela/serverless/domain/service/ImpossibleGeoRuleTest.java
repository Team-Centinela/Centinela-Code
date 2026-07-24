package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImpossibleGeoRuleTest {

    private static final double NYC_LAT = 40.7128;
    private static final double NYC_LNG = -74.0060;
    private static final double LA_LAT = 34.0522;
    private static final double LA_LNG = -118.2437;

    private final TransactionReceivedEvent currentTx = new TransactionReceivedEvent(
            UUID.randomUUID(), "acc-geo", new BigDecimal("100.00"), "USD",
            "merchant-1", LA_LAT, LA_LNG, Instant.now()
    );

    @Test
    void shouldTriggerWhenImpossibleTravel() {
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, currentTx.timestamp().minus(15, ChronoUnit.MINUTES)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent(), "NYC to LA in 15 min should be impossible");
        assertEquals("FR-3", result.get().ruleCode());
    }

    @Test
    void shouldNotTriggerWhenPlausibleTravel() {
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, currentTx.timestamp().minus(6, ChronoUnit.HOURS)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "NYC to LA in 6 hours should be plausible");
    }

    @Test
    void shouldNotTriggerWhenNoPreviousTransaction() {
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "first-ever tx should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenPreviousHasNoLocation() {
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", null, null, currentTx.timestamp().minus(15, ChronoUnit.MINUTES)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "prev tx with no location should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenCurrentHasNoLocation() {
        var noLocCurrent = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("100.00"), "USD",
                "merchant-1", null, null, Instant.now()
        );
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, Instant.now().minus(15, ChronoUnit.MINUTES)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(noLocCurrent);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "current tx with no location should NOT trigger");
    }

    @Test
    void shouldNotTriggerWhenTimestampsAreEqual() {
        Instant sameTime = Instant.now();
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, sameTime
        );
        var sameTimeCurrent = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("100.00"), "USD",
                "merchant-1", LA_LAT, LA_LNG, sameTime
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(sameTimeCurrent);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "same timestamp should NOT trigger (timeDelta=0)");
    }

    @Test
    void rawEvidenceContainsCorrectFields() {
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, currentTx.timestamp().minus(15, ChronoUnit.MINUTES)
        );
        var cfgRepo = new StubConfigRepo(Optional.empty());
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isPresent());
        Map<String, Object> evidence = result.get().rawEvidence();
        assertNotNull(evidence.get("currentLocation"));
        assertNotNull(evidence.get("previousLocation"));
        assertEquals(true, evidence.get("physicallyImpossible"));
        assertTrue(evidence.containsKey("distanceKm"));
        assertTrue(evidence.containsKey("timeDeltaMinutes"));
        assertTrue(evidence.containsKey("maxPossibleKm"));
    }

    @Test
    void shouldUseConfigOverrides() {
        var prevTx = new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-geo", new BigDecimal("50.00"), "USD",
                "merchant-1", NYC_LAT, NYC_LNG, currentTx.timestamp().minus(15, ChronoUnit.MINUTES)
        );
        var cfg = new RuleConfig("FR-3", true, Map.of(
                "maxSpeedKmph", 16000.0,
                "score", 50
        ));
        var cfgRepo = new StubConfigRepo(Optional.of(cfg));
        var rule = new ImpossibleGeoRule(cfgRepo);
        var ctx = new EvaluationContext(currentTx);
        ctx.previousTransaction(prevTx);

        Optional<TriggeredRule> result = rule.evaluate(ctx);

        assertTrue(result.isEmpty(), "with maxSpeed=16000, NYC-LA in 15min is plausible");
    }

    @Test
    void haversineProducesCorrectDistance() {
        double distance = ImpossibleGeoRule.haversine(NYC_LAT, NYC_LNG, LA_LAT, LA_LNG);
        assertEquals(3944.0, distance, 10.0, "NYC-LA should be ~3944 km");
    }

    @Test
    void haversineReturnsZeroForSamePoint() {
        double distance = ImpossibleGeoRule.haversine(NYC_LAT, NYC_LNG, NYC_LAT, NYC_LNG);
        assertEquals(0.0, distance, 0.001);
    }

    private record StubConfigRepo(Optional<RuleConfig> config) implements RuleConfigRepository {
        @Override
        public Optional<RuleConfig> findByRuleCode(String ruleCode) {
            return config;
        }
    }
}
