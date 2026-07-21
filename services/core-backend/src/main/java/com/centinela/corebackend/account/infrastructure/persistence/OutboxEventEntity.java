package com.centinela.corebackend.account.infrastructure.persistence;

import com.centinela.corebackend.account.domain.port.OutboxRepository.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "AccountOutboxEvent")
@Table(name = "outbox_events", schema = "outbox")
class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String aggregateType;

    @Column(nullable = false, length = 255)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Column(nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(String eventType, String aggregateType, String aggregateId,
                      String payload, Status status) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.retryCount = 0;
        this.status = status;
    }
}
