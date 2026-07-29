package com.centinela.shared.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asserts that {@link OutboxPublisher} propagates {@code outbox_events.id} (the
 * outbox row's primary key) to the {@link ServiceBusPublisher} as the
 * {@code messageId} argument. This is the always-set contract the engine's
 * {@code TransactionsRawConsumer} (H7 fix, Phase 0.2.6) requires.
 */
class OutboxPublisherMessageIdPropagationTest {

    private OutboxEventJpaRepository repository;
    private ServiceBusPublisher serviceBusPublisher;
    private JdbcTemplate jdbcTemplate;
    private MeterRegistry meterRegistry;
    private OutboxPublisher publisher;

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
                /* maxAttempts */ 10,
                /* staleThresholdMinutes */ 5,
                /* drainTimeoutMs */ 10_000L);
    }

    @Test
    void passesOutboxRowIdAsMessageIdToServiceBusPublisher() {
        UUID rowId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OutboxEventEntity event = newPendingEvent(rowId, "TransactionReceived", "tx-99", "{\"x\":1}");

        when(repository.findPendingForPublish(anyInt(), anyInt())).thenReturn(List.of(event));
        when(repository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.countByStatus(any())).thenReturn(0L);

        publisher.publishPendingEvents();

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(serviceBusPublisher).publish(
                eq("TransactionReceived"),
                eq("tx-99"),
                eq("{\"x\":1}"),
                messageIdCaptor.capture());
        assertThat(messageIdCaptor.getValue()).isEqualTo(rowId.toString());
    }

    @Test
    void passesDistinctMessageIdsForMultipleEvents() {
        UUID rowId1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID rowId2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        OutboxEventEntity e1 = newPendingEvent(rowId1, "TransactionReceived", "tx-1", "{}");
        OutboxEventEntity e2 = newPendingEvent(rowId2, "TransactionReceived", "tx-2", "{}");

        when(repository.findPendingForPublish(anyInt(), anyInt())).thenReturn(List.of(e1, e2));
        when(repository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.countByStatus(any())).thenReturn(0L);

        publisher.publishPendingEvents();

        verify(serviceBusPublisher).publish(any(), eq("tx-1"), any(), eq(rowId1.toString()));
        verify(serviceBusPublisher).publish(any(), eq("tx-2"), any(), eq(rowId2.toString()));
    }

    private OutboxEventEntity newPendingEvent(UUID id, String eventType, String aggregateId, String payload) {
        OutboxEventEntity e = new OutboxEventEntity(eventType, "Transaction", aggregateId, payload,
                OutboxEventEntity.Status.PENDING);
        // The constructor assigns UUID.randomUUID(); overwrite with the test's id
        // so the assertion can check exact equality.
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
