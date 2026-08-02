package com.centinela.serverless.adapter.in.consumer;

import com.centinela.serverless.application.ScoreTransactionService;
import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
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
import java.util.function.Consumer;

/**
 * Functional inbound adapter for the {@code transactions-raw} Service Bus topic
 * subscription {@code serverless-engine} (ADR-003 §3.1, ADR-009 §9.1).
 * Replaces the deleted {@code infrastructure/messaging/TransactionsRawConsumer.java}
 * from PR #130 after the merge-reconciliation commit; lives in the BASE-aligned
 * {@code adapter/in/consumer/} package, which had only an empty
 * {@code package-info.java} marker until now.
 *
 * <p>Spring Cloud Stream binds the functional bean
 * ({@code transactionsRawIn()}) to the topic subscription using the bean name
 * suffix {@code -in-0}:</p>
 * <pre>
 *   spring.cloud.stream.bindings.transactionsRawIn-in-0.destination = transactions-raw
 *   spring.cloud.stream.bindings.transactionsRawIn-in-0.group        = serverless-engine
 * </pre>
 *
 * <p>Per-message flow:</p>
 * <ol>
 *   <li>Extract a {@code messageId} from the Service Bus message-id property.
 *       If absent, log WARN and throw — synthesizing a UUID here would silently
 *       defeat dedup on every redelivery (H7 fix). Per the always-set contract
 *       (Phase 0 §0.2.6 / §30.5 S2 #2) the producer (Ingestion Outbox Publisher)
 *       MUST always populate the {@code messageId} header; this consumer never
 *       falls back to a synthesized value.</li>
 *   <li>Open a CONSUMER span via {@link TraceparentPropagator} so the saga trace
 *       (ADR-007 §7.2) survives the broker hop end-to-end.</li>
 *   <li>Claim the message via {@link ReceivedMessageIdempotencyService.claim}
 *       using ADR-003 §3.3.2 ({@code SELECT FOR UPDATE SKIP LOCKED}).</li>
 *   <li>On {@code CLAIMED}, hand off to
 *       {@link ScoreTransactionService#score} which runs the
 *       {@code FraudPipeline}, persists {@code triggered_rules}, appends the
 *       {@code FraudEvaluationCompleted} event to the outbox, and advances the
 *       {@code received_messages} row to {@code PROCESSED} — all in one
 *       transaction. (BLOCKER 4 fix closure.)</li>
 *   <li>On {@code DUPLICATE_DONE} / {@code INFLIGHT_OTHER} / {@code RACE_LOST},
 *       return without re-processing. The binder ACKs and Spring error handling
 *       is not invoked.</li>
 * </ol>
 *
 * <p>This bean is gated on {@code spring.cloud.stream.enabled=true}; the
 * test profile (with binder excluded) skips it cleanly.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.cloud.stream", name = "enabled", havingValue = "true")
public class TransactionsRawConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionsRawConsumer.class);

    public static final String CONSUMER_NAME = "serverless-engine.transactions-raw";
    public static final String ENTITY_NAME = "transactions-raw";

    private final ObjectMapper objectMapper;
    private final ReceivedMessageIdempotencyService idempotency;
    private final ScoreTransactionService scoreTransactionService;
    private final TraceparentPropagator traceparent;
    private final Tracer tracer;

    public TransactionsRawConsumer(ObjectMapper objectMapper,
                                   ReceivedMessageIdempotencyService idempotency,
                                   ScoreTransactionService scoreTransactionService,
                                   TraceparentPropagator traceparent,
                                   Tracer tracer) {
        this.objectMapper = objectMapper;
        this.idempotency = idempotency;
        this.scoreTransactionService = scoreTransactionService;
        this.traceparent = traceparent;
        this.tracer = tracer;
    }

    @Bean
    public Consumer<Message<String>> transactionsRawIn() {
        return this::handle;
    }

    void handle(Message<String> message) {
        Map<String, Object> headers = message.getHeaders();
        String messageId = headerAsString(headers, "messageId");
        if (messageId == null || messageId.isBlank()) {
            log.warn("transactions-raw message has no messageId header; refusing to "
                            + "synthesize one (that would defeat deduplication). payload={}",
                    truncate(message.getPayload()));
            throw new IllegalArgumentException("messageId header is required");
        }

        Span span = traceparent.startConsumerSpan(ENTITY_NAME, headers);
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            TransactionReceivedEvent event = deserialize(message.getPayload());
            span.tag("transaction.id", event.transactionId().toString());

            ReceivedMessageIdempotencyService.ClaimResult claim =
                    idempotency.claim(CONSUMER_NAME, messageId, event.transactionId());
            span.tag("idempotency.outcome", claim.outcome().name());

            if (!claim.claimed()) {
                log.debug("Skipping messageId={} due to outcome={} (no re-processing)",
                        messageId, claim.outcome());
                return;
            }

            FraudDecision decision = scoreTransactionService.score(claim.row(), message.getPayload());
            log.info("Processed messageId={} transactionId={} recommendation={} score={}",
                    messageId, decision.transactionId(), decision.recommendation(), decision.totalScore());
        } catch (RuntimeException e) {
            log.warn("Failed to process messageId={}: {}", messageId, e.getMessage());
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private TransactionReceivedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransactionReceivedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid transactions-raw payload: " + e.getOriginalMessage(), e);
        }
    }

    private static String headerAsString(Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object raw = headers.get(key);
        return raw == null ? null : raw.toString();
    }

    private static String truncate(String payload) {
        if (payload == null) {
            return "null";
        }
        return payload.length() <= 256 ? payload : payload.substring(0, 256) + "...";
    }
}
