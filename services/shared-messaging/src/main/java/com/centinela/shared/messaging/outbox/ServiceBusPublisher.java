package com.centinela.shared.messaging.outbox;

public interface ServiceBusPublisher {
    void publish(String eventType, String aggregateId, String payload);
}