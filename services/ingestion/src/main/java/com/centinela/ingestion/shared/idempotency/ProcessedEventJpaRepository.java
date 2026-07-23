package com.centinela.ingestion.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.ProcessedEventId> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO outbox.processed_events (consumer, idempotency_key, processed_at)
        VALUES (:consumer, :key, NOW())
        ON CONFLICT (consumer, idempotency_key) DO NOTHING
        """, nativeQuery = true)
    int tryInsert(@Param("consumer") String consumer, @Param("key") String idempotencyKey);
}
