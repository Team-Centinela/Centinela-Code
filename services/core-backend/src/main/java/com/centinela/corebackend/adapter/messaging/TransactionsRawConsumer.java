package com.centinela.corebackend.adapter.messaging;

import com.centinela.shared.messaging.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class TransactionsRawConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionsRawConsumer.class);

    private final IdempotencyService idempotencyService;

    public TransactionsRawConsumer(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Bean
    public Consumer<Message<String>> transactionsRaw() {
        return message -> {
            String aggregateId = message.getHeaders().get("aggregateId", String.class);
            if (aggregateId == null) {
                log.warn("Received message without aggregateId header, skipping");
                return;
            }

            if (!idempotencyService.tryProcess("core-backend", aggregateId)) {
                return;
            }

            log.info("Processing transaction aggregateId={}", aggregateId);
        };
    }
}
