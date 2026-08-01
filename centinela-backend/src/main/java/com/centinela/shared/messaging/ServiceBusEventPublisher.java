package com.centinela.shared.messaging;

import com.centinela.shared.events.DomainEvent;
import com.centinela.shared.events.EventPublisher;
import com.azure.messaging.servicebus.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("azure")
public class ServiceBusEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ServiceBusEventPublisher.class);

    @Value("${centinela.servicebus.namespace:}")
    private String namespace;

    @Value("${centinela.servicebus.queue.ingestion:transacciones-ingestion}")
    private String queueName;

    private ServiceBusSenderClient senderClient;
    private ServiceBusClientBuilder clientBuilder;

    @PostConstruct
    public void init() {
        if (namespace == null || namespace.isBlank()) {
            log.warn("Service Bus namespace no configurado, usando publisher local");
            return;
        }

        try {
            clientBuilder = new ServiceBusClientBuilder()
                    .credential(namespace, new com.azure.identity.DefaultAzureCredentialBuilder().build());

            senderClient = clientBuilder
                    .sender()
                    .queueName(queueName)
                    .buildClient();

            log.info("Service Bus publisher inicializado para cola: {}", queueName);
        } catch (Exception e) {
            log.error("Error inicializando Service Bus publisher: {}", e.getMessage());
        }
    }

    @Override
    public void publish(DomainEvent event) {
        if (senderClient == null) {
            log.warn("Service Bus no disponible, evento {} no publicado", event.getEventType());
            return;
        }

        try {
            String body = com.fasterxml.jackson.databind.ObjectMapper.class
                    .getDeclaredConstructor().newInstance()
                    .writeValueAsString(event);

            ServiceBusMessage message = new ServiceBusMessage(body)
                    .setContentType("application/json")
                    .setSubject(event.getEventType())
                    .setMessageId(event.getEventId());

            senderClient.sendMessage(message);
            log.info("Evento {} publicado en Service Bus", event.getEventType());
        } catch (Exception e) {
            log.error("Error publicando evento en Service Bus: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (senderClient != null) {
            senderClient.close();
        }
    }
}
