package com.centinela.ingestion.domain.port;

import java.time.Instant;
import java.util.UUID;

public final class OutboxEvent {
    private final UUID id;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant createdAt;
    private final int retryCount;

    public OutboxEvent(UUID id, String aggregateType, String aggregateId,
                       String eventType, String payload, Instant createdAt, int retryCount) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.retryCount = retryCount;
    }

    public UUID id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public String payload() {
        return payload;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public int retryCount() {
        return retryCount;
    }
}
