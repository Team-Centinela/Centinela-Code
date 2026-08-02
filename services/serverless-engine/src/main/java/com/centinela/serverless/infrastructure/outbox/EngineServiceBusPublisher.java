package com.centinela.serverless.infrastructure.outbox;

public interface EngineServiceBusPublisher {
    void publish(String eventType, String aggregateId, String payload);
}
