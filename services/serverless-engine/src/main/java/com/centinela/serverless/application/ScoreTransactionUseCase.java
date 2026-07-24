package com.centinela.serverless.application;

import com.centinela.serverless.domain.model.FraudScore;
import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.OutboxEventAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Use case: score a single transaction.
 *
 * <p>Orchestrates the rule pipeline and persists both the {@code FraudEvaluationCompleted}
 * event domain-event payload <b>and</b> the outbox row in the same PostgreSQL transaction
 * (Outbox Pattern per ADR-003 §3.2 / ADR-004).</p>
 *
 * <p>The persistence half (raw evidence JSONB on {@code oltp.transactions.triggered_rules})
 * is owned by @3105jero and is <b>not</b> implemented here; the call site placeholder grows
 * a follow-up commit once the evidence migration lands.</p>
 */
@Component
public class ScoreTransactionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScoreTransactionUseCase.class);

    public static final String EVALUATION_COMPLETED_EVENT_TYPE = "FraudEvaluationCompleted";

    private final FraudEvaluationPipeline pipeline;
    private final OutboxEventAppender outboxAppender;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final double scoreThreshold;

    public ScoreTransactionUseCase(FraudEvaluationPipeline pipeline,
                                   OutboxEventAppender outboxAppender,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry,
                                   @org.springframework.beans.factory.annotation.Value("${fraud.rule.score-threshold:60.0}") double scoreThreshold) {
        this.pipeline = pipeline;
        this.outboxAppender = outboxAppender;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.scoreThreshold = scoreThreshold;
    }

    @Transactional
    public FraudScore evaluate(TransactionMessage tx) {
        FraudScore score = pipeline.evaluate(tx);
        Map<String, Object> envelope = toEnvelope(tx, score);
        outboxAppender.append(
                EVALUATION_COMPLETED_EVENT_TYPE,
                "transaction",
                tx.transactionId().toString(),
                serialize(envelope));
        recordBusinessCounter(score);
        log.debug("Scored transaction {} -> score={} flagged={} firedRules={}",
                tx.transactionId(), score.score(), score.flagged(),
                score.triggeredRules().stream().filter(TriggeredRule::isFired).map(TriggeredRule::ruleCode).toList());
        return score;
    }

    private Map<String, Object> toEnvelope(TransactionMessage tx, FraudScore score) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", EVALUATION_COMPLETED_EVENT_TYPE);
        envelope.put("eventVersion", "1.0");
        envelope.put("eventTime", Instant.now().toString());
        envelope.put("correlationId", tx.transactionId().toString());
        envelope.put("transactionId", tx.transactionId().toString());
        envelope.put("accountId", tx.accountId());
        envelope.put("score", score.score());
        envelope.put("flagged", score.flagged());
        envelope.put("threshold", callThreshold(score));
        envelope.put("triggeredRules", score.triggeredRules());
        envelope.put("explanation", score.explanation());
        return envelope;
    }

    private double callThreshold(FraudScore ignored) {
        return scoreThreshold;
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize FraudEvaluationCompleted envelope", e);
        }
    }

    private void recordBusinessCounter(FraudScore score) {
        String result = score.flagged() ? "case_created" : (score.hasAnyFired() ? "stored_only" : "stored_only");
        Counter.builder("centinela.transactions.evaluated")
                .description("Transactions processed through rule pipeline")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
