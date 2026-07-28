package com.centinela.serverless.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
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
public interface EngineOutboxEventJpaRepository extends JpaRepository<EngineOutboxEventEntity, UUID> {

    @Query("""
        SELECT e FROM EngineOutboxEvent e
        WHERE e.status = 'PENDING'
        ORDER BY e.createdAt ASC
        """)
    List<EngineOutboxEventEntity> findPendingForPublish(Pageable pageable);

    long countByStatus(EngineOutboxEventEntity.Status status);

    @Query("""
        SELECT MIN(e.createdAt) FROM EngineOutboxEvent e
        WHERE e.status = 'PENDING'
        """)
    Instant findOldestPendingCreatedAt();

    @Modifying
    @Transactional
    @Query("""
        UPDATE EngineOutboxEvent e
        SET e.status = 'PENDING', e.attempts = 0, e.lastAttemptAt = NULL
        WHERE e.status = 'PENDING'
          AND e.lastAttemptAt < :cutoff
        """)
    int resetStalePending(@Param("cutoff") Instant cutoff);
}
