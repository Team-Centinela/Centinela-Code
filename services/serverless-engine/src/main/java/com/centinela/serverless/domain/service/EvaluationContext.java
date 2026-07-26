package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EvaluationContext {
    private final TransactionReceivedEvent sourceTransaction;
    private final List<TriggeredRule> triggeredRules;
    private int accumulatedScore;
    private BigDecimal historicalAvg;
    private TransactionReceivedEvent previousTransaction;

    public EvaluationContext(TransactionReceivedEvent sourceTransaction) {
        this.sourceTransaction = sourceTransaction;
        this.triggeredRules = new ArrayList<>();
        this.accumulatedScore = 0;
    }

    public TransactionReceivedEvent sourceTransaction() {
        return sourceTransaction;
    }

    public List<TriggeredRule> triggeredRules() {
        return Collections.unmodifiableList(triggeredRules);
    }

    public int accumulatedScore() {
        return accumulatedScore;
    }

    public BigDecimal historicalAvg() {
        return historicalAvg;
    }

    public void historicalAvg(BigDecimal historicalAvg) {
        this.historicalAvg = historicalAvg;
    }

    public TransactionReceivedEvent previousTransaction() {
        return previousTransaction;
    }

    public void previousTransaction(TransactionReceivedEvent previousTransaction) {
        this.previousTransaction = previousTransaction;
    }

    public void addTriggeredRule(TriggeredRule rule) {
        triggeredRules.add(rule);
        accumulatedScore += rule.score();
    }
}
