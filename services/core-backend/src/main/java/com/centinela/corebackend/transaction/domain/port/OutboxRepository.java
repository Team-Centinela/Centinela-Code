package com.centinela.corebackend.transaction.domain.port;

/**
 * Port for durably recording an event alongside a transaction aggregate change.
 */
public interface OutboxRepository {

    enum Status {
        PENDING, SENT, DEAD_LETTER
    }

    void append(Status status, String aggregateType, String aggregateId,
                String payloadJson, String eventType);
}
