package com.centinela.shared.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DomainEvent {
    private final String eventId;
    private final String eventType;
    private final Instant timestamp;
    private final Map<String, Object> payload;

    public DomainEvent(String eventType, Map<String, Object> payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getPayload() { return payload; }
}
