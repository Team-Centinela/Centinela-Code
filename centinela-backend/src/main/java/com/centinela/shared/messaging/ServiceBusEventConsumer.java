package com.centinela.shared.messaging;

import com.centinela.scoring.application.service.ScoringService;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.shared.events.DomainEvent;
import com.azure.messaging.servicebus.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
@Profile("azure")
public class ServiceBusEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ServiceBusEventConsumer.class);

    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;

    @Value("${centinela.servicebus.namespace:}")
    private String namespace;

    @Value("${centinela.servicebus.queue.ingestion:transacciones-ingestion}")
    private String queueName;

    private ServiceBusProcessorClient processorClient;

    public ServiceBusEventConsumer(ScoringService scoringService, ObjectMapper objectMapper) {
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

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
                    .queueName(queueName)
                    .processMessage(this::processMessage)
                    .processError(this::processError)
                    .buildProcessorClient();

            processorClient.start();
            log.info("Service Bus consumer iniciado para cola: {}", queueName);
        } catch (Exception e) {
            log.error("Error iniciando Service Bus consumer: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        log.info("Mensaje recibido de Service Bus: {}", message.getMessageId());

        try {
            String body = message.getBody().toString();
            DomainEvent event = objectMapper.readValue(body, DomainEvent.class);

            if ("TRANSACTION_RECEIVED".equals(event.getEventType())) {
                Map<String, Object> payload = event.getPayload();
                TransactionHistory th = new TransactionHistory();
                th.setTransactionId((String) payload.get("transactionId"));
                th.setCuentaId((String) payload.get("cuentaId"));
                th.setMonto(new BigDecimal((String) payload.get("monto")));
                th.setMarcaTiempo(Instant.parse((String) payload.get("marcaTiempo")));
                th.setUbicacionLat((Double) payload.get("ubicacionLat"));
                th.setUbicacionLon((Double) payload.get("ubicacionLon"));
                th.setComercioId((String) payload.get("comercioId"));
                th.setComercioCategoria((String) payload.get("comercioCategoria"));

                scoringService.evaluarTransaccion(th);
                log.info("Transaccion {} procesada via Service Bus", th.getTransactionId());
            }

            context.complete();
        } catch (Exception e) {
            log.error("Error procesando mensaje de Service Bus: {}", e.getMessage(), e);
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
