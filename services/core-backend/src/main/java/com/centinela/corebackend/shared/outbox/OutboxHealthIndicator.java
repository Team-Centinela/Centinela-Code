package com.centinela.corebackend.shared.outbox;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxPublisher outboxPublisher;

    public OutboxHealthIndicator(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    public Health health() {
        if (outboxPublisher.isDegraded()) {
            return Health.down()
                    .withDetail("status", "DEGRADED")
                    .withDetail("reason", "Database not reachable on startup")
                    .build();
        }
        return Health.up()
                .withDetail("status", "UP")
                .build();
    }
}
