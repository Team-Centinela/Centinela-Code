package com.centinela.shared.messaging;

import com.centinela.shared.events.DomainEvent;
import com.centinela.shared.events.EventTypes;
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
public class ServiceBusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ServiceBusEventConsumer.class);

    @Value("${centinela.servicebus.namespace:}")
    private String namespace;

    @Value("${centinela.servicebus.topic.cases:casos-fraude}")
    private String topicName;

    @Value("${centinela.servicebus.subscription.gestion:gestion-casos}")
    private String subscriptionName;

    private ServiceBusProcessorClient processorClient;

    @PostConstruct
    public void init() {
        if (namespace == null || namespace.isBlank()) {
            log.warn("Service Bus namespace no configurado, consumer no iniciado");
            return;
        }

        try {
            ServiceBusClientBuilder clientBuilder = new ServiceBusClientBuilder()
                    .credential(namespace, new com.azure.identity.DefaultAzureCredentialBuilder().build());

            processorClient = clientBuilder
                    .processor()
                    .topicName(topicName)
                    .subscriptionName(subscriptionName)
                    .processMessage(this::processMessage)
                    .processError(this::processError)
                    .buildProcessorClient();

            processorClient.start();
            log.info("Service Bus consumer iniciado para topic: {}, subscription: {}", topicName, subscriptionName);
        } catch (Exception e) {
            log.error("Error iniciando Service Bus consumer: {}", e.getMessage());
        }
    }

    private void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        log.info("Mensaje recibido de Service Bus: {}", message.getMessageId());

        try {
            String body = message.getBody().toString();
            log.debug("Contenido: {}", body);
            context.complete();
        } catch (Exception e) {
            log.error("Error procesando mensaje: {}", e.getMessage());
            context.abandon();
        }
    }

    private void processError(ServiceBusErrorContext context) {
        log.error("Error en Service Bus: {}", context.getException().getMessage());
    }

    @PreDestroy
    public void cleanup() {
        if (processorClient != null) {
            processorClient.close();
        }
    }
}
