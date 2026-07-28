package com.centinela.serverless.domain.port;

import com.centinela.serverless.domain.model.TriggeredRule;

import java.util.List;
import java.util.UUID;

public interface TriggeredRuleRepository {
    void saveAll(List<TriggeredRule> rules, UUID transactionId);
    List<TriggeredRule> findByTransactionId(UUID transactionId);
}
