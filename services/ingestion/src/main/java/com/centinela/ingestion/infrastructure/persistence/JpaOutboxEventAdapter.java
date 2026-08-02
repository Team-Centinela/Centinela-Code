package com.centinela.ingestion.infrastructure.persistence;

import com.centinela.ingestion.domain.port.OutboxEvent;
import com.centinela.ingestion.domain.port.OutboxEventPort;
import com.centinela.shared.messaging.outbox.OutboxEventEntity;
import com.centinela.shared.messaging.outbox.OutboxEventJpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Adapter implementing {@link OutboxEventPort} against the shared-messaging
 * outbox infrastructure. Per the §0.3 unblocker (commit 97c5773, PR #266),
 * the canonical {@link OutboxEventEntity} + {@link OutboxEventJpaRepository}
 * live in {@code com.centinela.shared.messaging.outbox}; this module owns
 * only the domain port + the translation from the domain event shape to the
 * shared entity.
 */
@Repository
public class JpaOutboxEventAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository jpaRepository;

    public JpaOutboxEventAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity(
                event.eventType(),
                event.aggregateType(),
                event.aggregateId(),
                event.payload(),
                OutboxEventEntity.Status.PENDING
        );
        jpaRepository.save(entity);
    }
}