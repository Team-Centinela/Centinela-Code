package com.centinela.serverless.domain.port;

public interface OutboxEventAppender {
    void append(String eventType, String aggregateType, String aggregateId, String payload);
}
