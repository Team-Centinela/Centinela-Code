package com.centinela.corebackend.account.infrastructure.persistence;

import com.centinela.corebackend.account.domain.port.OutboxRepository;
import org.springframework.stereotype.Repository;

@Repository("accountJpaOutboxRepository")
public class JpaOutboxRepository implements OutboxRepository {

    private final AccountOutboxEventJpaRepository jpaRepository;

    public JpaOutboxRepository(AccountOutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void append(Status status, String aggregateType, String aggregateId,
                       String payloadJson, String eventType) {
        jpaRepository.save(new OutboxEventEntity(eventType, aggregateType, aggregateId, payloadJson, status));
    }
}
