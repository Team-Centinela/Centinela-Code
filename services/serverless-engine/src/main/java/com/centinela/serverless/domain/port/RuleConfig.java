package com.centinela.serverless.domain.port;

import java.math.BigDecimal;
import java.util.Map;

public record RuleConfig(
        String ruleCode,
        boolean enabled,
        Map<String, Object> config
) {
    public RuleConfig {
        if (ruleCode == null || ruleCode.isBlank()) throw new IllegalArgumentException("ruleCode must not be blank");
        if (config == null) throw new IllegalArgumentException("config must not be null");
    }

    /**
     * Returns the integer value at {@code key} or {@code defaultValue} when
     * absent. Strict per SrLampi1001 review on PR #268 (finding 5):
     * <ul>
     *   <li>Rejects fractional numbers — {@code 1.9} is NOT silently truncated
     *       to {@code 1}; an {@link IllegalStateException} surfaces the
     *       config drift at rule-load time.</li>
     *   <li>Rejects out-of-range values — a {@code Long} larger than
     *       {@link Integer#MAX_VALUE} or smaller than {@link Integer#MIN_VALUE}
     *       throws instead of overflowing during {@code intValue()}.</li>
     *   <li>Rejects non-finite doubles (NaN, ±Infinity).</li>
     * </ul>
     */
    public int getInt(String key, int defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        if (!(raw instanceof Number n)) {
            throw new IllegalStateException(
                    "RuleConfig[" + ruleCode + "]." + key + " expected int, got " + raw.getClass().getSimpleName()
                            + " (value=" + raw + ")");
        }
        // Fractional detection: numeric types that COULD carry a fractional
        // component must be checked explicitly. Integer/Long/Short/Byte are
        // always integral so the check is skipped for them.
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalStateException(
                        "RuleConfig[" + ruleCode + "]." + key + " expected int, got non-finite " + d);
            }
            if (d != Math.floor(d)) {
                throw new IllegalStateException(
                        "RuleConfig[" + ruleCode + "]." + key + " expected int, got fractional " + d);
            }
        } else if (n instanceof BigDecimal bd) {
            if (bd.signum() != 0 && bd.remainder(BigDecimal.ONE).signum() != 0) {
                throw new IllegalStateException(
                        "RuleConfig[" + ruleCode + "]." + key + " expected int, got fractional " + bd.toPlainString());
            }
        }
        // Overflow detection: a Long / Double whose value exceeds Integer range
        // would silently wrap via intValue(). Compare via longValue() which
        // preserves magnitude.
        long asLong = n.longValue();
        if (asLong > Integer.MAX_VALUE || asLong < Integer.MIN_VALUE) {
            throw new IllegalStateException(
                    "RuleConfig[" + ruleCode + "]." + key + " value " + asLong
                            + " is outside int range [" + Integer.MIN_VALUE + ", " + Integer.MAX_VALUE + "]");
        }
        return (int) asLong;
    }

    /**
     * Returns the double value at {@code key} or {@code defaultValue} when
     * absent. Strict per SrLampi1001 review on PR #268 (finding 5):
     * <ul>
     *   <li>Rejects non-finite values (NaN, ±Infinity).</li>
     *   <li>BigDecimal values are converted with {@code doubleValue()}
     *       (the caller accepts IEEE-754 rounding for non-financial fields;
     *       financial fields should use {@code getBigDecimal}).</li>
     * </ul>
     */
    public double getDouble(String key, double defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        if (!(raw instanceof Number n)) {
            throw new IllegalStateException(
                    "RuleConfig[" + ruleCode + "]." + key + " expected double, got " + raw.getClass().getSimpleName()
                            + " (value=" + raw + ")");
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalStateException(
                    "RuleConfig[" + ruleCode + "]." + key + " expected finite double, got " + d);
        }
        return d;
    }

    /**
     * Returns the BigDecimal value at {@code key} or {@code defaultValue}
     * when absent. ADR-004 §4.2 + #246: financial fields should use this
     * getter rather than {@code getDouble} to preserve precision through
     * the JSONB round-trip.
     */
    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof BigDecimal bd) return bd;
        if (raw instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (raw instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "RuleConfig[" + ruleCode + "]." + key + " expected BigDecimal, got unparseable string '" + s + "'");
            }
        }
        throw new IllegalStateException(
                "RuleConfig[" + ruleCode + "]." + key + " expected BigDecimal, got " + raw.getClass().getSimpleName()
                        + " (value=" + raw + ")");
    }

    public String getString(String key, String defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        return raw.toString();
    }
}
