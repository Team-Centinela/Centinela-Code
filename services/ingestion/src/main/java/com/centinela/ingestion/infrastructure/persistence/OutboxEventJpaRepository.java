package com.centinela.ingestion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT * FROM outbox.outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findPending(@Param("limit") int limit);

    @Query(value = """
            SELECT * FROM outbox.outbox_events
            WHERE status = 'PENDING' AND attempts > 0 AND last_attempt_at < :olderThan
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findStalePending(@Param("olderThan") Instant olderThan,
                                             @Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE outbox.outbox_events
            SET status = 'PUBLISHED', published_at = NOW()
            WHERE id = :eventId
            """, nativeQuery = true)
    void markPublished(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = """
            UPDATE outbox.outbox_events
            SET status = 'DEAD_LETTER'
            WHERE id = :eventId
            """, nativeQuery = true)
    void markDeadLetter(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = """
            UPDATE outbox.outbox_events
            SET attempts = attempts + 1, last_attempt_at = NOW()
            WHERE id = :eventId
            """, nativeQuery = true)
    void incrementAttempt(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = """
            UPDATE outbox.outbox_events
            SET attempts = 0, last_attempt_at = NULL
            WHERE status = 'PENDING'
            AND last_attempt_at < NOW() - INTERVAL '5 minutes'
            AND attempts > 0
            LIMIT :limit
            """, nativeQuery = true)
    int resetStaleAttempts(@Param("limit") int limit);

    @Query(value = """
            SELECT COUNT(*) FROM outbox.outbox_events WHERE status = :status
            """, nativeQuery = true)
    long countByStatus(@Param("status") String status);

    @Query(value = """
            SELECT MIN(created_at) FROM outbox.outbox_events WHERE status = :status
            """, nativeQuery = true)
    Instant oldestByStatus(@Param("status") String status);
}
