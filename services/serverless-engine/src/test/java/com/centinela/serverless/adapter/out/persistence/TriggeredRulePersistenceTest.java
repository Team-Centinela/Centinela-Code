package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.TriggeredRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class TriggeredRulePersistenceTest {

    @Autowired
    private TriggeredRuleRepository repository;

    @Test
    void shouldPersistAndRetrieveTriggeredRules() {
        UUID txId = UUID.randomUUID();
        var rules = List.of(
                new TriggeredRule("FR-1", 20,
                        evMap("window_seconds", 300, "txn_count", 11, "threshold", 10, "current_score_added", 20),
                        Instant.now()),
                new TriggeredRule("FR-4", 30,
                        evMap("merchant_id", "merchant-fraud", "risk_label", "HIGH", "current_score_added", 30),
                        Instant.now())
        );

        repository.saveAll(rules, txId);
        List<TriggeredRule> retrieved = repository.findByTransactionId(txId);

        assertEquals(2, retrieved.size());
        var fr1 = retrieved.stream().filter(r -> r.ruleCode().equals("FR-1")).findFirst().orElseThrow();
        assertEquals(20, fr1.score());
        assertEquals(300, fr1.rawEvidence().get("window_seconds"));
        assertEquals(11, fr1.rawEvidence().get("txn_count"));

        var fr4 = retrieved.stream().filter(r -> r.ruleCode().equals("FR-4")).findFirst().orElseThrow();
        assertEquals(30, fr4.score());
        assertEquals("merchant-fraud", fr4.rawEvidence().get("merchant_id"));
        assertEquals("HIGH", fr4.rawEvidence().get("risk_label"));
    }

    @Test
    void shouldReturnEmptyListWhenNoRulesForTransaction() {
        List<TriggeredRule> retrieved = repository.findByTransactionId(UUID.randomUUID());
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void shouldValidateFr1Schema() {
        var valid = new TriggeredRule("FR-1", 20,
                evMap("window_seconds", 300, "txn_count", 11, "threshold", 10, "current_score_added", 20),
                Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));

        var invalid = new TriggeredRule("FR-1", 20,
                evMap("window_seconds", 300, "txn_count", 11),
                Instant.now());
        assertThrows(IllegalArgumentException.class, () -> RawEvidenceValidator.validate(invalid));
    }

    @Test
    void shouldValidateFr2Schema() {
        var valid = new TriggeredRule("FR-2", 25,
                evMap("current_amount_usd", 200.0, "historical_avg_usd", 100.0, "historical_sample_size", 50L,
                      "std_dev_usd", 20.0, "z_score", 5.0, "current_score_added", 25),
                Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));
    }

    @Test
    void shouldValidateFr3Schema() {
        var valid = new TriggeredRule("FR-3", 25,
                evMap("last_txn_lat", 40.71, "last_txn_lon", -74.00, "last_txn_at", "2026-07-24T09:45:00Z",
                      "current_lat", 34.05, "current_lon", -118.24,
                      "distance_km", 3944.2, "elapsed_seconds", 900L,
                      "implied_speed_kmh", 15776.0, "max_allowed_speed_kmh", 800.0,
                      "current_score_added", 25),
                Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));
    }

    @Test
    void shouldValidateFr4Schema() {
        var valid = new TriggeredRule("FR-4", 30,
                evMap("merchant_id", "m-1", "risk_label", "HIGH", "current_score_added", 30),
                Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));
    }

    @Test
    void shouldSkipValidationForUnknownRuleCode() {
        var rule = new TriggeredRule("UNKNOWN", 0, evMap("anything", "value"), Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(rule));
    }

    @Test
    void shouldValidateMultipleRules() {
        var rules = List.of(
                new TriggeredRule("FR-1", 20,
                        evMap("window_seconds", 300, "txn_count", 11, "threshold", 10, "current_score_added", 20),
                        Instant.now()),
                new TriggeredRule("FR-4", 30,
                        evMap("merchant_id", "m-1", "risk_label", "HIGH", "current_score_added", 30),
                        Instant.now())
        );
        assertDoesNotThrow(() -> RawEvidenceValidator.validateAll(rules));

        var invalid = List.of(
                new TriggeredRule("FR-1", 20,
                        evMap("window_seconds", 300, "txn_count", 11),  // missing threshold + current_score_added
                        Instant.now()),
                new TriggeredRule("FR-4", 30,
                        evMap("merchant_id", "m-1", "risk_label", "HIGH", "current_score_added", 30),
                        Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () -> RawEvidenceValidator.validateAll(invalid));
    }

    private static Map<String, Object> evMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
