package com.centinela.serverless.infrastructure.outbox;

import com.centinela.serverless.domain.port.OutboxEventAppender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaOutboxEventAppender implements OutboxEventAppender {

    private final EngineOutboxEventJpaRepository repository;

    public JpaOutboxEventAppender(EngineOutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String eventType, String aggregateType, String aggregateId, String payload) {
        repository.save(new EngineOutboxEventEntity(eventType, aggregateType, aggregateId, payload));
    }
}
