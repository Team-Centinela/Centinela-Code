package com.centinela.shared.messaging.outbox;

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
    private final String caseEventsBinding;
    private final String documentsPendingBinding;
    private final String fraudEvaluationBinding;

    public ServiceBusPublisherImpl(StreamBridge streamBridge,
                                   @Value("${spring.cloud.stream.bindings.transactions-raw-out-0.destination:transactions-raw}") String transactionsRawBinding,
                                   @Value("${spring.cloud.stream.bindings.case-events-out-0.destination:case-events}") String caseEventsBinding,
                                   @Value("${spring.cloud.stream.bindings.documents-pending-out-0.destination:documents-pending}") String documentsPendingBinding,
                                   @Value("${spring.cloud.stream.bindings.fraud-evaluation-out-0.destination:fraud-evaluation}") String fraudEvaluationBinding) {
        this.streamBridge = streamBridge;
        this.transactionsRawBinding = transactionsRawBinding;
        this.caseEventsBinding = caseEventsBinding;
        this.documentsPendingBinding = documentsPendingBinding;
        this.fraudEvaluationBinding = fraudEvaluationBinding;
    }

    @Override
    public void publish(String eventType, String aggregateId, String payload) {
        String binding = resolveBinding(eventType);
        boolean sent = streamBridge.send(binding, MessageBuilder.withPayload(payload)
                .setHeader("eventType", eventType)
                .setHeader("aggregateId", aggregateId)
                .build());
        if (!sent) {
            throw new IllegalStateException("Failed to send message to binding: " + binding);
        }
        log.debug("Published event {} to binding {} with aggregateId {}", eventType, binding, aggregateId);
    }

    private String resolveBinding(String eventType) {
        return switch (eventType) {
            case "TransactionReceived" -> transactionsRawBinding;
            case "FraudEvaluationCompleted" -> fraudEvaluationBinding;
            case "CaseCreated", "AlertCreated", "CaseUpdated" -> caseEventsBinding;
            case "DocumentUploaded" -> documentsPendingBinding;
            default -> {
                log.warn("Unknown event type {}, defaulting to transactions-raw binding", eventType);
                yield transactionsRawBinding;
            }
        };
    }
}