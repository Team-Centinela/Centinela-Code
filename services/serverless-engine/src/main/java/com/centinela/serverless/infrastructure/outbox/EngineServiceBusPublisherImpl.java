package com.centinela.serverless.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class EngineServiceBusPublisherImpl implements EngineServiceBusPublisher {

    private static final Logger log = LoggerFactory.getLogger(EngineServiceBusPublisherImpl.class);

    private final StreamBridge streamBridge;
    private final String caseEventsBinding;

    public EngineServiceBusPublisherImpl(StreamBridge streamBridge,
                                         @Value("${spring.cloud.stream.bindings.case-events-out-0.destination:case-events}") String caseEventsBinding) {
        this.streamBridge = streamBridge;
        this.caseEventsBinding = caseEventsBinding;
    }

    @Override
    public void publish(String eventType, String aggregateId, String payload) {
        boolean sent = streamBridge.send(caseEventsBinding, MessageBuilder.withPayload(payload)
                .setHeader("eventType", eventType)
                .setHeader("aggregateId", aggregateId)
                .build());
        if (!sent) {
            throw new IllegalStateException("Failed to send to binding: " + caseEventsBinding);
        }
        log.debug("Published {} to {} (aggregateId={})", eventType, caseEventsBinding, aggregateId);
    }
}
