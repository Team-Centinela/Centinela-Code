package com.centinela.ingestion.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "processed_events", schema = "outbox")
@IdClass(ProcessedEventEntity.ProcessedEventId.class)
class ProcessedEventEntity {

    @Id
    @Column(name = "consumer", length = 100, nullable = false)
    private String consumer;

    @Id
    @Column(name = "idempotency_key", length = 255, nullable = false)
    private String idempotencyKey;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    ProcessedEventEntity(String consumer, String idempotencyKey) {
        this.consumer = consumer;
        this.idempotencyKey = idempotencyKey;
        this.processedAt = Instant.now();
    }

    static class ProcessedEventId implements Serializable {
        private String consumer;
        private String idempotencyKey;

        public ProcessedEventId() {
        }

        public ProcessedEventId(String consumer, String idempotencyKey) {
            this.consumer = consumer;
            this.idempotencyKey = idempotencyKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProcessedEventId that)) return false;
            return Objects.equals(consumer, that.consumer) && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumer, idempotencyKey);
        }
    }
}
