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
                new TriggeredRule("FR-1", 20, evMap("windowMinutes", 5, "transactionCount", 11, "threshold", 10), Instant.now()),
                new TriggeredRule("FR-4", 30, evMap("merchantId", "merchant-fraud", "flagged", true, "flaggedSince", "2026-01-15T10:00:00Z"), Instant.now())
        );

        repository.saveAll(rules, txId);
        List<TriggeredRule> retrieved = repository.findByTransactionId(txId);

        assertEquals(2, retrieved.size());
        var fr1 = retrieved.stream().filter(r -> r.ruleCode().equals("FR-1")).findFirst().orElseThrow();
        assertEquals(20, fr1.score());
        assertEquals(5, fr1.rawEvidence().get("windowMinutes"));
        assertEquals(11, fr1.rawEvidence().get("transactionCount"));

        var fr4 = retrieved.stream().filter(r -> r.ruleCode().equals("FR-4")).findFirst().orElseThrow();
        assertEquals(30, fr4.score());
        assertEquals("merchant-fraud", fr4.rawEvidence().get("merchantId"));
        assertEquals(true, fr4.rawEvidence().get("flagged"));
    }

    @Test
    void shouldReturnEmptyListWhenNoRulesForTransaction() {
        List<TriggeredRule> retrieved = repository.findByTransactionId(UUID.randomUUID());
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void shouldValidateFr1Schema() {
        var valid = new TriggeredRule("FR-1", 20, evMap("windowMinutes", 5, "transactionCount", 11, "threshold", 10), Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));

        var invalid = new TriggeredRule("FR-1", 20, evMap("windowMinutes", 5), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> RawEvidenceValidator.validate(invalid));
    }

    @Test
    void shouldValidateFr2Schema() {
        var valid = new TriggeredRule("FR-2", 25, evMap("amount", 200.0, "historicalAvg", 100.0, "historicalStdDev", 20.0, "zScore", 5.0, "threshold", 2.5), Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));
    }

    @Test
    void shouldValidateFr3Schema() {
        var valid = new TriggeredRule("FR-3", 25, evMap(
                "currentLocation", Map.of("lat", 34.05, "lng", -118.24),
                "previousLocation", Map.of("lat", 40.71, "lng", -74.00),
                "distanceKm", 3944.2, "timeDeltaMinutes", 15,
                "maxPossibleKm", 200.0, "physicallyImpossible", true
        ), Instant.now());
        assertDoesNotThrow(() -> RawEvidenceValidator.validate(valid));
    }

    @Test
    void shouldValidateFr4Schema() {
        var valid = new TriggeredRule("FR-4", 30, evMap("merchantId", "m-1", "flagged", true, "flaggedSince", "2026-01-15T10:00:00Z"), Instant.now());
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
                new TriggeredRule("FR-1", 20, evMap("windowMinutes", 5, "transactionCount", 11, "threshold", 10), Instant.now()),
                new TriggeredRule("FR-4", 30, evMap("merchantId", "m-1", "flagged", true, "flaggedSince", "2026-01-15T10:00:00Z"), Instant.now())
        );
        assertDoesNotThrow(() -> RawEvidenceValidator.validateAll(rules));

        var invalid = List.of(
                new TriggeredRule("FR-1", 20, evMap("windowMinutes", 5), Instant.now()),
                new TriggeredRule("FR-4", 30, evMap("merchantId", "m-1", "flagged", true, "flaggedSince", "2026-01-15T10:00:00Z"), Instant.now())
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
