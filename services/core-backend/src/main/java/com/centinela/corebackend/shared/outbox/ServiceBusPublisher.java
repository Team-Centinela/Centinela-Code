package com.centinela.corebackend.shared.outbox;

public interface ServiceBusPublisher {
    void publish(String eventType, String aggregateId, String payload);
}
