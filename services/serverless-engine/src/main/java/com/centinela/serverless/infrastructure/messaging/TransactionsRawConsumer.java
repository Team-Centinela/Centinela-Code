package com.centinela.serverless.infrastructure.messaging;

import com.centinela.serverless.application.ScoreTransactionUseCase;
import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageIdempotencyService;
import com.centinela.serverless.infrastructure.observability.TraceparentPropagator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Functional consumer of the {@code transactions-raw} Service Bus queue (per ADR-003 §3.1,
 * pinned to Spring Cloud Stream / Spring Cloud Azure binder in {@code services/pom.xml}).
 *
 * <ul>
 *   <li>Extracts W3C {@code traceparent} via {@link TraceparentPropagator}.</li>
 *   <li>Claims the message via {@link ReceivedMessageIdempotencyService} which uses
 *       {@code SELECT ... FOR UPDATE SKIP LOCKED} on {@code received_messages} per
 *       ADR-003 §3.3.2 — closing #41 for the engine side.</li>
 *   <li>If claimed, executes {@link ScoreTransactionUseCase}: runs the rule pipeline,
 *       persists evidence JSONB, and emits {@code FraudEvaluationCompleted} to the outbox,
 *       all in the same transaction.</li>
 *   <li>If duplicate / in-flight-other / race-lost, returns without re-processing — the
 *       binder ACKs the message and Spring {@code @StreamListener} error handling is
 *       not invoked.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.cloud.stream", name = "enabled", havingValue = "true", matchIfMissing = false)
public class TransactionsRawConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionsRawConsumer.class);

    public static final String CONSUMER_NAME = "serverless-engine.transactions-raw";
    public static final String ENTITY_NAME = "transactions-raw";

    private final ObjectMapper objectMapper;
    private final ReceivedMessageIdempotencyService idempotency;
    private final ScoreTransactionUseCase useCase;
    private final TraceparentPropagator traceparent;
    private final Tracer tracer;

    public TransactionsRawConsumer(ObjectMapper objectMapper,
                                   ReceivedMessageIdempotencyService idempotency,
                                   ScoreTransactionUseCase useCase,
                                   TraceparentPropagator traceparent,
                                   Tracer tracer) {
        this.objectMapper = objectMapper;
        this.idempotency = idempotency;
        this.useCase = useCase;
        this.traceparent = traceparent;
        this.tracer = tracer;
    }

    @Bean
    public Consumer<Message<String>> transactionsRaw() {
        return this::handle;
    }

    void handle(Message<String> message) {
        String payload = message.getPayload();
        Map<String, Object> headers = message.getHeaders();
        String messageId = headerAsString(headers, "messageId");
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        Span span = traceparent.startConsumerSpan(ENTITY_NAME, headers);
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            TransactionMessage tx = deserialize(payload);
            ReceivedMessageIdempotencyService.Outcome outcome = idempotency.claim(
                    CONSUMER_NAME, messageId, tx.transactionId());
            span.tag("idempotency.outcome", outcome.name());
            switch (outcome) {
                case CLAIMED -> useCase.evaluate(tx);
                case DUPLICATE_DONE, INFLIGHT_OTHER, RACE_LOST ->
                        log.debug("Skipping messageId={} due to outcome={}", messageId, outcome);
            }
        } catch (Exception e) {
            log.warn("Failed to process messageId={}: {}", messageId, e.getMessage());
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private TransactionMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransactionMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payload on transactions-raw: " + e.getMessage(), e);
        }
    }

    private static String headerAsString(Map<String, Object> headers, String key) {
        Object raw = headers.get(key);
        if (raw == null) {
            return null;
        }
        return raw.toString();
    }
}
