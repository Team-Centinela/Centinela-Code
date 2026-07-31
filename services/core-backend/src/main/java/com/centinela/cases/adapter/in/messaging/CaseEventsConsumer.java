package com.centinela.cases.adapter.in.messaging;

import com.centinela.cases.application.CreateCaseFromEvaluationUseCase;
import com.centinela.cases.application.CreateCaseFromEvaluationUseCase.FraudEvaluationCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Functional inbound adapter for the {@code case-events} Service Bus topic
 * subscription {@code core-backend-sub} (ADR-003 §3.1, ADR-009 §9.1).
 * Deserializes {@code FraudEvaluationCompleted} envelopes, hands off to
 * {@link CreateCaseFromEvaluationUseCase}, which applies the §3.3.1 dedup
 * ledger and the ADR-004 §4.4 case-creation decision.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.cloud.stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CaseEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CaseEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final CreateCaseFromEvaluationUseCase createCaseFromEvaluationUseCase;

    public CaseEventsConsumer(ObjectMapper objectMapper,
                             CreateCaseFromEvaluationUseCase createCaseFromEvaluationUseCase) {
        this.objectMapper = objectMapper;
        this.createCaseFromEvaluationUseCase = createCaseFromEvaluationUseCase;
    }

    @Bean
    public Consumer<Message<String>> caseEventsIn() {
        return this::handle;
    }

    void handle(Message<String> message) {
        String payload = message.getPayload();
        String aggregateId = headerString(message, "aggregateId");
        if (aggregateId == null) {
            aggregateId = extractTransactionIdFromPayload(payload);
        }
        if (aggregateId == null) {
            log.warn("case-events message has no aggregateId header and no transactionId in payload; skipping");
            return;
        }

        FraudEvaluationCompletedEvent event;
        try {
            event = parse(payload);
        } catch (JsonProcessingException e) {
            log.warn("Skipping malformed FraudEvaluationCompleted payload aggregateId={}: {}",
                    aggregateId, e.getOriginalMessage());
            return;
        }

        String traceId = headerString(message, "traceparent");
        FraudEvaluationCompletedEvent enriched = traceId == null
                ? event
                : new FraudEvaluationCompletedEvent(
                        event.eventType(),
                        event.eventVersion(),
                        event.eventTime(),
                        event.correlationId(),
                        event.transactionId(),
                        event.accountId(),
                        event.recommendation(),
                        event.score(),
                        event.triggeredRules(),
                        traceId);

        CreateCaseFromEvaluationUseCase.Outcome outcome =
                createCaseFromEvaluationUseCase.execute(enriched);
        log.info("Processed case-events aggregateId={} outcome={} recommendation={} score={}",
                aggregateId, outcome, enriched.recommendation(), enriched.score());
    }

    @SuppressWarnings("unchecked")
    private FraudEvaluationCompletedEvent parse(String payload) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(payload, Map.class);
        UUID transactionId = toUuid(map.get("transactionId"));
        UUID correlationId = toUuid(map.get("correlationId"));
        String accountId = map.get("accountId") == null ? null : map.get("accountId").toString();
        String recommendation = map.get("recommendation") == null ? null : map.get("recommendation").toString();
        int score = toInt(map.get("score"));
        Object rawRules = map.get("triggeredRules");
        List<Map<String, Object>> rules = rawRules instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        java.time.Instant eventTime = map.get("eventTime") instanceof String s
                ? java.time.Instant.parse(s)
                : null;
        String eventType = map.get("eventType") == null ? null : map.get("eventType").toString();
        String eventVersion = map.get("eventVersion") == null ? null : map.get("eventVersion").toString();
        return new FraudEvaluationCompletedEvent(
                eventType, eventVersion, eventTime, correlationId, transactionId,
                accountId, recommendation, score, rules, null);
    }

    private static String headerString(Message<String> message, String key) {
        Object value = message.getHeaders().get(key);
        return value == null ? null : value.toString();
    }

    private static String extractTransactionIdFromPayload(String payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(payload, Map.class);
            Object id = map.get("transactionId");
            return id == null ? null : id.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            return Integer.parseInt(s);
        }
        return 0;
    }
}
