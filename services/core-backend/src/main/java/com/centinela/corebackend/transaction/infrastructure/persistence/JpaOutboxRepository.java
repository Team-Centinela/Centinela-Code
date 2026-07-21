package com.centinela.corebackend.transaction.infrastructure.persistence;

import com.centinela.corebackend.transaction.domain.port.OutboxRepository;
import org.springframework.stereotype.Repository;

@Repository("transactionJpaOutboxRepository")
public class JpaOutboxRepository implements OutboxRepository {

    private final TransactionOutboxEventJpaRepository jpaRepository;

    public JpaOutboxRepository(TransactionOutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void append(Status status, String aggregateType, String aggregateId,
                       String payloadJson, String eventType) {
        jpaRepository.save(new OutboxEventEntity(eventType, aggregateType, aggregateId, payloadJson, status));
    }
}
