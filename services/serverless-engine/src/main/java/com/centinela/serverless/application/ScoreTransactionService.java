package com.centinela.serverless.application;

import com.centinela.serverless.adapter.out.persistence.RawEvidenceValidator;
import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.OutboxEventAppender;
import com.centinela.serverless.domain.port.TriggeredRuleRepository;
import com.centinela.serverless.domain.service.EvaluationContext;
import com.centinela.serverless.domain.service.FraudPipeline;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageIdempotencyService;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private final MeterRegistry meterRegistry;
    private final Counter scoredCounter;
    private final Counter caseCreatedCounter;
    private final Counter storedOnlyCounter;
    private final Timer totalTimer;

    public ScoreTransactionService(FraudPipeline pipeline,
                                  TriggeredRuleRepository triggeredRuleRepository,
                                  OutboxEventAppender outboxAppender,
                                  ReceivedMessageIdempotencyService idempotency,
                                  ObjectMapper objectMapper,
                                  MeterRegistry meterRegistry) {
        this.pipeline = pipeline;
        this.triggeredRuleRepository = triggeredRuleRepository;
        this.outboxAppender = outboxAppender;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        // Metrics described in docs/decision-log/ADR-007-observability-cost-telemetry.md
        // section 7.4 (business metrics). Both counters are pre-registered with the
        // full set of result-tag values so the metric exists at zero, which keeps
        // PromQL queries like sum by (result)(...) consistent from the first message.
        this.scoredCounter = Counter.builder("centinela.transactions.evaluated")
                .description("Transactions processed through rule pipeline")
                .tag("result", "scored")
                .register(meterRegistry);
        this.caseCreatedCounter = Counter.builder("centinela.transactions.evaluated")
                .description("Transactions processed through rule pipeline")
                .tag("result", "case_created")
                .register(meterRegistry);
        this.storedOnlyCounter = Counter.builder("centinela.transactions.evaluated")
                .description("Transactions processed through rule pipeline")
                .tag("result", "stored_only")
                .register(meterRegistry);
        this.totalTimer = Timer.builder("centinela.evaluation.duration_ms")
                .description("Rule pipeline total latency (pipeline + persist + outbox append)")
                .tag("stage", "total")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
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
        long startNanos = System.nanoTime();
        TransactionReceivedEvent event = deserialize(payload);
        EvaluationContext ctx = new EvaluationContext(event);
        FraudDecision decision = pipeline.execute(ctx);

        // ADR-004 §4.2: every fired rule must carry the pinned rawEvidence key
        // set for its rule code. Reject the message before persistence so a
        // schema drift surfaces as a binder DLQ instead of an Analytics query
        // that returns null on the join.
        RawEvidenceValidator.validateAll(decision.triggeredRules());

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

        // ADR-007 §7.4: emit business metrics. Mapping rules:
        //   Recommendation.BLOCK -> case_created (the saga has opened a case)
        //   Recommendation.FLAG  -> scored with extra rule hits
        //   Recommendation.APPROVE -> scored (rule pipeline ran, nothing tripped)
        // 'stored_only' is reserved for the future when the engine persists a
        // transaction without running the rule pipeline (today this never happens
        // and the counter stays at zero); keeping it in the meter means PromQL
        // queries are stable across a future feature addition.
        evaluateCounter(decision.recommendation()).increment();
        recordRuleTriggers(decision.triggeredRules());
        totalTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);

        log.info("transactionId={} recommendation={} score={} firedRules=[{}]",
                decision.transactionId(),
                decision.recommendation(),
                decision.totalScore(),
                decision.triggeredRules().stream().map(TriggeredRule::ruleCode).toList());
        return decision;
    }

    private Counter evaluateCounter(Recommendation recommendation) {
        return switch (recommendation) {
            case BLOCK -> caseCreatedCounter;
            case FLAG, APPROVE -> scoredCounter;
        };
    }

    private void recordRuleTriggers(java.util.List<TriggeredRule> rules) {
        // BASE's FraudPipeline only adds a TriggeredRule to the context if it
        // fired (see FraudPipeline.execute + AggregatorStage.evaluate). The
        // TriggeredRule record does not carry an isFired() flag, so every
        // TriggeredRule in this list is implicitly 'fired'.
        for (TriggeredRule rule : rules) {
            Counter.builder("centinela.rule.triggered")
                    .description("Per-rule trigger counts")
                    .tag("rule_code", rule.ruleCode())
                    .tag("result", "fired")
                    .register(meterRegistry)
                    .increment();
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
