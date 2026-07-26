package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.model.TriggeredRule;

import java.util.Optional;

public interface PipelineStage {
    Optional<TriggeredRule> evaluate(EvaluationContext ctx);
}
