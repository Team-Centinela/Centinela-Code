package com.centinela.serverless.domain.model;

public enum Recommendation {
    APPROVE,
    FLAG,
    BLOCK;

    public static Recommendation fromScore(int totalScore) {
        if (totalScore >= 70) return BLOCK;
        if (totalScore >= 30) return FLAG;
        return APPROVE;
    }
}
