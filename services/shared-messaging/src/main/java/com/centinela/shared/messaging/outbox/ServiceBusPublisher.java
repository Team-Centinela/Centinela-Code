package com.centinela.shared.messaging.outbox;

/**
 * Publishes a domain event to Azure Service Bus.
 *
 * <p>The {@code messageId} parameter is the deterministic identifier the consumer
 * uses for inbound-message dedup (ADR-003 §3.3.1). It MUST be supplied by the
 * caller — derived from the outbox row's primary key — and MUST be non-null and
 * non-blank. The implementation MUST set it on every published message; the
 * always-set contract assumed by {@code TransactionsRawConsumer} (Phase 0.2.6 /
 * §30.5 S2 #2) is broken otherwise, and legitimate redeliveries get spuriously
 * dead-lettered.</p>
 */
public interface ServiceBusPublisher {

    /**
     * @param messageId deterministic event id (typically {@code outbox_events.id}
     *                  rendered as a String); MUST be non-null and non-blank.
     * @throws IllegalArgumentException if {@code messageId} is null or blank.
     */
    void publish(String eventType, String aggregateId, String payload, String messageId);
}