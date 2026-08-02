package com.centinela.shared.messaging.outbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies #272 acceptance criteria:
 *   - OutboxPublisher's failure log appends the underlying exception so every
 *     future failure is diagnosable without grep archaeology.
 *   - The row stays PENDING across transient failures (ADR-003 §3.2 lifecycle).
 *   - The row transitions to DEAD_LETTER after maxAttempts (ADR-003 §3.4).
 *
 * Same root-cause class as previously-closed #180 / #255 / PR #262; the SLF4J
 * contract "last arg as Throwable is logged with stack trace" must hold here.
 */
class OutboxPublisherFailurePathTest {

    private OutboxEventJpaRepository repository;
    private ServiceBusPublisher serviceBusPublisher;
    private JdbcTemplate jdbcTemplate;
    private MeterRegistry meterRegistry;
    private OutboxPublisher publisher;

    private Logger publisherLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventJpaRepository.class);
        serviceBusPublisher = mock(ServiceBusPublisher.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        meterRegistry = new SimpleMeterRegistry();

        when(repository.findOldestPendingCreatedAt()).thenReturn(null);

        publisher = new OutboxPublisher(
                repository,
                serviceBusPublisher,
                jdbcTemplate,
                meterRegistry,
                /* batchSize */ 100,
                /* maxAttempts */ 3,
                /* staleThresholdMinutes */ 5,
                /* drainTimeoutMs */ 10_000L);

        publisherLogger = (Logger) LoggerFactory.getLogger(OutboxPublisher.class);
        previousLevel = publisherLogger.getLevel();
        publisherLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        publisherLogger.addAppender(appender);

        when(repository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.countByStatus(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        publisherLogger.detachAppender(appender);
        publisherLogger.setLevel(previousLevel);
    }

    @Test
    void singleFailureKeepsEventPendingAndLogsThrowableWithStackTrace() {
        UUID rowId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OutboxEventEntity event = newPendingEvent(rowId, "TransactionReceived", "tx-1", "{}");
        // attempts is 1 (incremented in the first pass before publish)
        RuntimeException underlying = new RuntimeException(
                "Connection refused: centinela-servicebus/172.18.0.5:5671");

        when(repository.findPendingForPublish(anyInt(), anyInt())).thenReturn(List.of(event));
        doThrow(underlying).when(serviceBusPublisher).publish(any(), any(), any(), any());

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventEntity.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);

        ILoggingEvent failureLog = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("Failed to publish outbox event"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected a WARN 'Failed to publish outbox event' log entry; got: "
                                + appender.list));
        assertThat(failureLog.getThrowableProxy())
                .as("SLF4J last-arg-as-Throwable contract: stack trace must be logged, "
                        + "not just .getMessage(). See #272 / #180 / #255.")
                .isNotNull();
        assertThat(failureLog.getThrowableProxy().getMessage())
                .contains("Connection refused");
    }

    @Test
    void eventTransitionsToDeadLetterAfterMaxAttempts() {
        UUID rowId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        OutboxEventEntity event = newPendingEvent(rowId, "TransactionReceived", "tx-2", "{}");
        // Pre-increment attempts to maxAttempts - 1 so that the publisher's first-pass
        // increment tips it over the threshold and the catch block applies DEAD_LETTER.
        event.incrementAttempts();
        event.incrementAttempts();

        when(repository.findPendingForPublish(anyInt(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("simulated broker outage"))
                .when(serviceBusPublisher).publish(any(), any(), any(), any());

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventEntity.Status.DEAD_LETTER);

        ILoggingEvent deadLetterLog = appender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .filter(e -> e.getFormattedMessage().contains("DEAD_LETTER"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected an ERROR 'DEAD_LETTER' log entry; got: " + appender.list));
        assertThat(deadLetterLog.getThrowableProxy())
                .as("DEAD_LETTER log must carry the underlying exception for diagnosability")
                .isNotNull();
    }

    private OutboxEventEntity newPendingEvent(UUID id, String eventType, String aggregateId, String payload) {
        OutboxEventEntity e = new OutboxEventEntity(eventType, "Transaction", aggregateId, payload,
                OutboxEventEntity.Status.PENDING);
        try {
            java.lang.reflect.Field idField = OutboxEventEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }
}