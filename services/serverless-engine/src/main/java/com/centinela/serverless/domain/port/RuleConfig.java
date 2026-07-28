package com.centinela.serverless.domain.port;

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

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        if (config.containsKey(key)) {
            return (T) config.get(key);
        }
        return defaultValue;
    }
}
