package com.centinela.ingestion.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class ServiceBusPublisherImpl implements ServiceBusPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServiceBusPublisherImpl.class);

    private final StreamBridge streamBridge;
    private final String transactionsRawBinding;

    public ServiceBusPublisherImpl(StreamBridge streamBridge,
                                   @Value("${spring.cloud.stream.bindings.transactions-raw-out-0.destination:transactions-raw}") String transactionsRawBinding) {
        this.streamBridge = streamBridge;
        this.transactionsRawBinding = transactionsRawBinding;
    }

    @Override
    public void publish(String eventType, String aggregateId, String payload) {
        boolean sent = streamBridge.send(transactionsRawBinding, MessageBuilder.withPayload(payload)
                .setHeader("eventType", eventType)
                .setHeader("aggregateId", aggregateId)
                .build());
        if (!sent) {
            throw new IllegalStateException("Failed to send message to binding: " + transactionsRawBinding);
        }
        log.debug("Published event {} to binding {} with aggregateId {}", eventType, transactionsRawBinding, aggregateId);
    }
}
