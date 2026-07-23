package com.centinela.ingestion.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
        SELECT * FROM outbox.outbox_events
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :limit OFFSET :offset
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForPublish(@Param("limit") int limit, @Param("offset") int offset);

    long countByStatus(OutboxEventEntity.Status status);

    @Query("""
        SELECT MIN(e.createdAt) FROM OutboxEventEntity e
        WHERE e.status = 'PENDING'
        """)
    Instant findOldestPendingCreatedAt();

    @Modifying
    @Transactional
    @Query("""
        UPDATE OutboxEventEntity e
        SET e.status = 'PENDING', e.attempts = 0, e.lastAttemptAt = NULL
        WHERE e.status = 'PENDING'
          AND e.lastAttemptAt < :cutoff
        """)
    int resetStalePending(@Param("cutoff") Instant cutoff);
}
