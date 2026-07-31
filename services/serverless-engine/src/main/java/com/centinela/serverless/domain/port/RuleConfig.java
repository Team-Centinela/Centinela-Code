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

    public int getInt(String key, int defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number n) return n.intValue();
        throw new IllegalStateException(
                "RuleConfig[" + ruleCode + "]." + key + " expected int, got " + raw.getClass().getSimpleName()
                        + " (value=" + raw + ")");
    }

    public double getDouble(String key, double defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof BigDecimal bd) return bd.doubleValue();
        throw new IllegalStateException(
                "RuleConfig[" + ruleCode + "]." + key + " expected double, got " + raw.getClass().getSimpleName()
                        + " (value=" + raw + ")");
    }

    public String getString(String key, String defaultValue) {
        Object raw = config.get(key);
        if (raw == null) return defaultValue;
        return raw.toString();
    }
}
