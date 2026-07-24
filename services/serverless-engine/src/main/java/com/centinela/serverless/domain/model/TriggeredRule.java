package com.centinela.serverless.domain.model;

public record TriggeredRule(
        String ruleCode,
        double score,
        Object rawEvidence,
        String fireStatus
) {
    public static TriggeredRule notFired(String ruleCode) {
        return new TriggeredRule(ruleCode, 0.0d, new java.util.HashMap<>(), "not_fired");
    }

    public static TriggeredRule fired(String ruleCode, double score, Object rawEvidence) {
        return new TriggeredRule(ruleCode, score, rawEvidence, "fired");
    }

    public boolean isFired() {
        return "fired".equals(fireStatus);
    }
}
