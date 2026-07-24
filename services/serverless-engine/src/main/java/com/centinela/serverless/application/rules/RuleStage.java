package com.centinela.serverless.application.rules;

import com.centinela.serverless.domain.model.FraudScore;
import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;

import java.util.List;

public interface RuleStage {
    String ruleCode();

    TriggeredRule evaluate(TransactionMessage tx);
}
