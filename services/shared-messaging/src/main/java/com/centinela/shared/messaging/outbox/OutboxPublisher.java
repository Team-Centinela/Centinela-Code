package com.centinela.shared.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventJpaRepository outboxRepository;
    private final ServiceBusPublisher serviceBusPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration staleThreshold;
    private final long drainTimeoutMs;
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong(0);
    private volatile boolean publisherDegraded = false;

    public OutboxPublisher(OutboxEventJpaRepository outboxRepository,
                           ServiceBusPublisher serviceBusPublisher,
                           JdbcTemplate jdbcTemplate,
                           MeterRegistry meterRegistry,
                           @Value("${outbox.publisher.batch-size:100}") int batchSize,
                           @Value("${outbox.publisher.max-attempts:10}") int maxAttempts,
                           @Value("${outbox.publisher.stale-threshold-minutes:5}") int staleThresholdMinutes,
                           @Value("${outbox.publisher.drain-timeout-ms:10000}") long drainTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.serviceBusPublisher = serviceBusPublisher;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.staleThreshold = Duration.ofMinutes(staleThresholdMinutes);
        this.drainTimeoutMs = drainTimeoutMs;
        registerMetrics();
    }

    private void registerMetrics() {
        Gauge.builder("outbox.pending.count", pendingCount, AtomicInteger::get)
                .description("Number of pending outbox events")
                .register(meterRegistry);
        Gauge.builder("outbox.pending.oldest.seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest pending outbox event")
                .register(meterRegistry);
    }

    @PostConstruct
    public void onStartup() {
        log.info("OutboxPublisher starting - waiting for database readiness...");
        boolean dbReady = false;
        int attempts = 0;
        int maxAttempts = 6;
        while (!dbReady && attempts < maxAttempts) {
            try {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                dbReady = true;
                log.info("Database connection established on attempt {}", attempts + 1);
            } catch (Exception e) {
                attempts++;
                log.warn("Database not ready (attempt {}: {})", attempts, maxAttempts, e.getMessage());
                if (attempts < maxAttempts) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        if (!dbReady) {
            publisherDegraded = true;
            log.error("Database not reachable after {} attempts - publisher entering DEGRADED mode", maxAttempts);
        } else {
            log.info("OutboxPublisher started successfully");
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("OutboxPublisher shutting down - draining pending events (timeout={}ms)...", drainTimeoutMs);
        try {
            Thread.sleep(drainTimeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("OutboxPublisher shutdown complete");
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        if (publisherDegraded) {
            try {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                publisherDegraded = false;
                log.info("Database connection recovered - publisher ACTIVE");
            } catch (Exception e) {
                log.debug("Database still unreachable, publisher remains DEGRADED");
                return;
            }
        }

        List<OutboxEventEntity> events = outboxRepository.findPendingForPublish(batchSize, 0);
        if (events.isEmpty()) {
            updateMetrics(0, 0);
            return;
        }

        log.debug("Claimed {} pending outbox events for publishing", events.size());

        // First pass: mark all as ATTEMPTING (increment attempts, set lastAttemptAt)
        Instant now = Instant.now();
        for (OutboxEventEntity event : events) {
            event.setLastAttemptAt(now);
            event.incrementAttempts();
        }
        outboxRepository.saveAllAndFlush(events);

        // Second pass: publish to Service Bus
        int published = 0;
        for (OutboxEventEntity event : events) {
            try {
                serviceBusPublisher.publish(event.getEventType(), event.getAggregateId(), event.getPayload());
                event.setStatus(OutboxEventEntity.Status.PUBLISHED);
                event.setPublishedAt(Instant.now());
                published++;
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(OutboxEventEntity.Status.DEAD_LETTER);
                    log.error("Event {} moved to DEAD_LETTER after {} attempts", event.getId(), maxAttempts);
                }
                // else keep as PENDING for retry
            }
        }

        // Single batch flush for all status updates
        outboxRepository.saveAllAndFlush(events);

        updateMetrics(outboxRepository.countByStatus(OutboxEventEntity.Status.PENDING), getOldestPendingAge());
        log.debug("Published {} out of {} claimed events", published, events.size());
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void recoverStalePending() {
        Instant cutoff = Instant.now().minus(staleThreshold);
        int resetCount = outboxRepository.resetStalePending(cutoff);
        if (resetCount > 0) {
            log.info("Recovered {} stale PENDING outbox events (older than {})", resetCount, staleThreshold);
        }
    }

    private void updateMetrics(long pending, long oldestAgeSeconds) {
        pendingCount.set((int) pending);
        oldestPendingAgeSeconds.set(oldestAgeSeconds);
    }

    private long getOldestPendingAge() {
        Instant oldest = outboxRepository.findOldestPendingCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Duration.between(oldest, Instant.now()).getSeconds();
    }

    public boolean isDegraded() {
        return publisherDegraded;
    }
}