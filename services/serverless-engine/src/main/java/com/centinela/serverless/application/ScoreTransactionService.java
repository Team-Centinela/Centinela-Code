package com.centinela.serverless.application;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.OutboxEventAppender;
import com.centinela.serverless.domain.port.TriggeredRuleRepository;
import com.centinela.serverless.domain.service.EvaluationContext;
import com.centinela.serverless.domain.service.FraudPipeline;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageIdempotencyService;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-layer orchestration for scoring a single {@code transactions-raw}
 * message. This is the ACID transaction boundary for the Serverless Engine
 * (ADR-003 §3.2 + §3.4 + ADR-004 §4.3):
 *
 * <ol>
 *   <li>Run {@code FraudPipeline.execute(ctx)} to produce a {@link FraudDecision}
 *       (an {@link EvaluationContext} pre-loaded with the source event).</li>
 *   <li>Persist the triggered-rule audit trail via
 *       {@link TriggeredRuleRepository#saveAll(java.util.List, java.util.UUID)}.</li>
 *   <li>Append a {@code FraudEvaluationCompleted} domain event to the
 *       outbox via {@link OutboxEventAppender}.</li>
 *   <li>Advance the {@code received_messages} row from {@code RECEIVED} to
 *       {@code PROCESSED} via {@link ReceivedMessageIdempotencyService#markProcessed}
 *       so a future redelivery sees {@code Outcome.DUPLICATE_DONE} and is ACKed
 *       without re-processing. (BLOCKER 4 fix.)</li>
 * </ol>
 *
 * <p>Any throw rolls back the transaction. The binder's retry/DLQ
 * machinery then re-attempts the whole flow from the
 * {@code SELECT FOR UPDATE} gate (ADR-003 §3.4).</p>
 */
@Service
public class ScoreTransactionService {

    private static final Logger log = LoggerFactory.getLogger(ScoreTransactionService.class);

    public static final String EVALUATION_COMPLETED_EVENT_TYPE = "FraudEvaluationCompleted";
    public static final String EVENT_VERSION = "1.0";

    private final FraudPipeline pipeline;
    private final TriggeredRuleRepository triggeredRuleRepository;
    private final OutboxEventAppender outboxAppender;
    private final ReceivedMessageIdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public ScoreTransactionService(FraudPipeline pipeline,
                                  TriggeredRuleRepository triggeredRuleRepository,
                                  OutboxEventAppender outboxAppender,
                                  ReceivedMessageIdempotencyService idempotency,
                                  ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.triggeredRuleRepository = triggeredRuleRepository;
        this.outboxAppender = outboxAppender;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluate + persist + outbox + ledger close.
     *
     * @param ledgerRow the {@code received_messages} row claimed via
     *     {@link ReceivedMessageIdempotencyService.ClaimResult#row()}; must be
     *     non-null (callers gate on {@link ReceivedMessageIdempotencyService.ClaimResult#claimed()})
     * @param payload Service Bus message body, JSON-encoded {@code TransactionReceivedEvent}
     * @return the resulting {@link FraudDecision}
     */
    @Transactional
    public FraudDecision score(ReceivedMessageRepository.Row ledgerRow, String payload) {
        if (ledgerRow == null) {
            throw new IllegalArgumentException(
                    "ledgerRow must not be null; the caller must gate on claim().claimed()");
        }
        TransactionReceivedEvent event = deserialize(payload);
        EvaluationContext ctx = new EvaluationContext(event);
        FraudDecision decision = pipeline.execute(ctx);

        triggeredRuleRepository.saveAll(decision.triggeredRules(), decision.transactionId());
        log.debug("Stored {} triggered rule(s) for transaction {} (recommendation={}, score={})",
                decision.triggeredRules().size(), decision.transactionId(),
                decision.recommendation(), decision.totalScore());

        outboxAppender.append(
                EVALUATION_COMPLETED_EVENT_TYPE,
                "transaction",
                decision.transactionId().toString(),
                serialize(buildEnvelope(event, decision)));

        idempotency.markProcessed(ledgerRow);

        log.info("transactionId={} recommendation={} score={} firedRules=[{}]",
                decision.transactionId(),
                decision.recommendation(),
                decision.totalScore(),
                decision.triggeredRules().stream().map(TriggeredRule::ruleCode).toList());
        return decision;
    }

    private TransactionReceivedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, TransactionReceivedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid transactions-raw payload: " + e.getOriginalMessage(), e);
        }
    }

    private Map<String, Object> buildEnvelope(TransactionReceivedEvent event, FraudDecision decision) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", EVALUATION_COMPLETED_EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("eventTime", Instant.now().toString());
        envelope.put("correlationId", event.transactionId().toString());
        envelope.put("transactionId", event.transactionId().toString());
        envelope.put("accountId", event.accountId());
        envelope.put("recommendation", decision.recommendation());
        envelope.put("score", decision.totalScore());
        envelope.put("triggeredRules", decision.triggeredRules());
        return envelope;
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize FraudEvaluationCompleted envelope", e);
        }
    }
}
