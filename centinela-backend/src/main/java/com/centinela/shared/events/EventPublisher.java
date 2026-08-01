package com.centinela.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EventPublisher {
    Logger log = LoggerFactory.getLogger(EventPublisher.class);

    void publish(DomainEvent event);

    default void publishSafe(DomainEvent event) {
        try {
            publish(event);
        } catch (Exception e) {
            log.error("Error publicando evento {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }
}
