package com.centinela.serverless.domain.port;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RuleConfig} typed accessors.
 *
 * <p>SrLampi1001 review on PR #268 (finding 5): {@code getInt} must reject
 * fractional numbers (no silent {@code 1.9 → 1} truncation), reject
 * out-of-range values (no silent overflow past {@code Integer.MAX_VALUE}),
 * and reject non-finite doubles. {@code getDouble} must reject non-finite
 * values. These tests pin those guarantees.</p>
 */
class RuleConfigTest {

    private static RuleConfig cfgWith(Map<String, Object> config) {
        return new RuleConfig("FR-TEST", true, config);
    }

    @Test
    void getIntReturnsDefaultWhenKeyMissing() {
        var cfg = cfgWith(Map.of());
        assertEquals(42, cfg.getInt("missing", 42));
    }

    @Test
    void getIntReturnsIntegerValue() {
        var cfg = cfgWith(Map.of("score", 70));
        assertEquals(70, cfg.getInt("score", 0));
    }

    @Test
    void getIntAcceptsLongValueWithinRange() {
        var cfg = cfgWith(Map.of("score", 70L));
        assertEquals(70, cfg.getInt("score", 0));
    }

    /**
     * SrLampi1001 finding 5: 1.9 must NOT silently truncate to 1.
     */
    @Test
    void getIntRejectsFractionalDouble() {
        var cfg = cfgWith(Map.of("score", 1.9));
        var ex = assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
        assertTrue(ex.getMessage().contains("fractional"), "message must mention fractional: " + ex.getMessage());
    }

    @Test
    void getIntRejectsFractionalFloat() {
        var cfg = cfgWith(Map.of("score", 1.9f));
        assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
    }

    @Test
    void getIntRejectsFractionalBigDecimal() {
        var cfg = cfgWith(Map.of("score", new BigDecimal("1.9")));
        var ex = assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
        assertTrue(ex.getMessage().contains("fractional"));
    }

    @Test
    void getIntAcceptsIntegralBigDecimal() {
        var cfg = cfgWith(Map.of("score", new BigDecimal("70.0000")));
        assertEquals(70, cfg.getInt("score", 0));
    }

    /**
     * SrLampi1001 finding 5: large values must NOT silently overflow.
     */
    @Test
    void getIntRejectsLongOverflow() {
        // Integer.MAX_VALUE + 1 = 2147483648; intValue() would wrap to Integer.MIN_VALUE.
        var cfg = cfgWith(Map.of("score", 2147483648L));
        var ex = assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
        assertTrue(ex.getMessage().contains("outside int range"),
                "message must call out the overflow: " + ex.getMessage());
    }

    @Test
    void getIntRejectsLongUnderflow() {
        var cfg = cfgWith(Map.of("score", -2147483649L));
        assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
    }

    @Test
    void getIntRejectsDoubleNaN() {
        var cfg = cfgWith(Map.of("score", Double.NaN));
        var ex = assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
        assertTrue(ex.getMessage().contains("non-finite"));
    }

    @Test
    void getIntRejectsDoubleInfinity() {
        var cfg = cfgWith(Map.of("score", Double.POSITIVE_INFINITY));
        assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
    }

    @Test
    void getIntRejectsStringValue() {
        var cfg = cfgWith(Map.of("score", "70"));
        var ex = assertThrows(IllegalStateException.class, () -> cfg.getInt("score", 0));
        assertTrue(ex.getMessage().contains("expected int, got String"));
    }

    @Test
    void getDoubleReturnsDefaultWhenKeyMissing() {
        var cfg = cfgWith(Map.of());
        assertEquals(2.5, cfg.getDouble("missing", 2.5));
    }

    @Test
    void getDoubleAcceptsInteger() {
        var cfg = cfgWith(Map.of("threshold", 30));
        assertEquals(30.0, cfg.getDouble("threshold", 0.0));
    }

    @Test
    void getDoubleRejectsNaN() {
        var cfg = cfgWith(Map.of("threshold", Double.NaN));
        assertThrows(IllegalStateException.class, () -> cfg.getDouble("threshold", 0.0));
    }

    @Test
    void getDoubleRejectsInfinity() {
        var cfg = cfgWith(Map.of("threshold", Double.POSITIVE_INFINITY));
        assertThrows(IllegalStateException.class, () -> cfg.getDouble("threshold", 0.0));
    }

    @Test
    void getDoubleAcceptsFractional() {
        // Fractional IS the explicit use case for getDouble (vs getInt).
        var cfg = cfgWith(Map.of("threshold", 2.5));
        assertEquals(2.5, cfg.getDouble("threshold", 0.0));
    }

    @Test
    void getDoubleRejectsStringValue() {
        var cfg = cfgWith(Map.of("threshold", "2.5"));
        assertThrows(IllegalStateException.class, () -> cfg.getDouble("threshold", 0.0));
    }

    @Test
    void getBigDecimalPreservesPrecision() {
        // #246 regression: 20-digit BigDecimal must survive the round-trip.
        var cfg = cfgWith(Map.of("z_score", new BigDecimal("5.01234567890123456789")));
        assertEquals(0, new BigDecimal("5.01234567890123456789")
                .compareTo(cfg.getBigDecimal("z_score", BigDecimal.ZERO)));
    }

    @Test
    void getBigDecimalAcceptsNumber() {
        var cfg = cfgWith(Map.of("amount", 100));
        assertEquals(0, new BigDecimal("100.0").compareTo(cfg.getBigDecimal("amount", BigDecimal.ZERO)));
    }

    @Test
    void getBigDecimalAcceptsString() {
        var cfg = cfgWith(Map.of("amount", "99.99"));
        assertEquals(0, new BigDecimal("99.99").compareTo(cfg.getBigDecimal("amount", BigDecimal.ZERO)));
    }

    @Test
    void getBigDecimalRejectsUnparseableString() {
        var cfg = cfgWith(Map.of("amount", "not-a-number"));
        assertThrows(IllegalStateException.class,
                () -> cfg.getBigDecimal("amount", BigDecimal.ZERO));
    }

    @Test
    void getStringReturnsToStringForNumber() {
        var cfg = cfgWith(Map.of("label", 42));
        assertEquals("42", cfg.getString("label", "fallback"));
    }

    @Test
    void constructorRejectsBlankRuleCode() {
        assertThrows(IllegalArgumentException.class, () -> new RuleConfig("", true, Map.of()));
    }

    @Test
    void constructorRejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> new RuleConfig("X", true, null));
    }

    @Test
    void mutableConfigMapIsSupported() {
        // RuleConfig stores the map by reference; mutations by callers
        // are visible to subsequent getInt calls. Document the behaviour
        // so future refactors preserve it intentionally.
        Map<String, Object> mutable = new HashMap<>();
        var cfg = new RuleConfig("FR-TEST", true, mutable);
        assertEquals(10, cfg.getInt("score", 10));
        mutable.put("score", 70);
        assertEquals(70, cfg.getInt("score", 10));
    }
}
