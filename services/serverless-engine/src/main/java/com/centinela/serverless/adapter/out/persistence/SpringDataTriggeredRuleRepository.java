package com.centinela.serverless.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataTriggeredRuleRepository extends JpaRepository<TriggeredRuleEntity, UUID> {
    List<TriggeredRuleEntity> findByTransactionId(UUID transactionId);
}
