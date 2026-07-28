package com.centinela.serverless.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class EngineOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(EngineOutboxPublisher.class);

    private final EngineOutboxEventJpaRepository repository;
    private final EngineServiceBusPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;

    public EngineOutboxPublisher(EngineOutboxEventJpaRepository repository,
                                 EngineServiceBusPublisher publisher,
                                 @Value("${outbox.publisher.batch-size:100}") int batchSize,
                                 @Value("${outbox.publisher.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<EngineOutboxEventEntity> events = repository.findPendingForPublish(PageRequest.of(0, batchSize));
        if (events.isEmpty()) {
            return;
        }
        int published = 0;
        for (EngineOutboxEventEntity event : events) {
            try {
                event.setLastAttemptAt(Instant.now());
                event.incrementAttempts();
                repository.saveAndFlush(event);
                publisher.publish(event.getEventType(), event.getAggregateId(), event.getPayload());
                event.setStatus(EngineOutboxEventEntity.Status.PUBLISHED);
                event.setPublishedAt(Instant.now());
                repository.saveAndFlush(event);
                published++;
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(EngineOutboxEventEntity.Status.DEAD_LETTER);
                }
                repository.saveAndFlush(event);
            }
        }
        log.debug("Engine outbox publisher published {} of {} claimed events", published, events.size());
    }
}
