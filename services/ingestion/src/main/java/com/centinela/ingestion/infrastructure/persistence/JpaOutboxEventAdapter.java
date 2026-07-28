package com.centinela.ingestion.infrastructure.persistence;

import com.centinela.ingestion.domain.port.OutboxEvent;
import com.centinela.ingestion.domain.port.OutboxEventPort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaOutboxEventAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository jpaRepository;

    public JpaOutboxEventAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity(
                event.id(),
                event.eventType(),
                event.aggregateId(),
                event.aggregateType(),
                event.payload(),
                "PENDING",
                event.retryCount(),
                null,
                event.createdAt(),
                null
        );
        jpaRepository.save(entity);
    }

    public List<OutboxEventEntity> findPending(int limit) {
        return jpaRepository.findPending(limit);
    }

    public List<OutboxEventEntity> findStalePending(Instant olderThan, int limit) {
        return jpaRepository.findStalePending(olderThan, limit);
    }

    public void markPublished(UUID eventId) {
        jpaRepository.markPublished(eventId);
    }

    public void markDeadLetter(UUID eventId) {
        jpaRepository.markDeadLetter(eventId);
    }

    public void incrementAttempt(UUID eventId) {
        jpaRepository.incrementAttempt(eventId);
    }

    public int resetStaleAttempts(int limit) {
        return jpaRepository.resetStaleAttempts(limit);
    }

    public long countByStatus(String status) {
        return jpaRepository.countByStatus(status);
    }

    public Instant oldestByStatus(String status) {
        return jpaRepository.oldestByStatus(status);
    }
}
