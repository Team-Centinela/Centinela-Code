package com.centinela.serverless.domain.port;

import java.util.Optional;

public interface RuleConfigRepository {
    Optional<RuleConfig> findByRuleCode(String ruleCode);
}
