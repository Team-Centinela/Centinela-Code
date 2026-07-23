package com.centinela.ingestion.domain.port;

import java.time.Instant;
import java.util.UUID;

public final class IdempotencyRecord {
    private final UUID id;
    private final String keyHash;
    private final String responseStatus;
    private final String responseBody;
    private final Instant createdAt;

    public IdempotencyRecord(UUID id, String keyHash, String responseStatus,
                             String responseBody, Instant createdAt) {
        this.id = id;
        this.keyHash = keyHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String keyHash() {
        return keyHash;
    }

    public String responseStatus() {
        return responseStatus;
    }

    public String responseBody() {
        return responseBody;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
