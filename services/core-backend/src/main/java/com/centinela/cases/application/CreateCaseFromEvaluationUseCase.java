package com.centinela.cases.application;

import com.centinela.cases.domain.model.Case;
import com.centinela.cases.domain.model.CaseStatus;
import com.centinela.cases.domain.port.CaseRepository;
import com.centinela.shared.messaging.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for opening a fraud case from a
 * {@code FraudEvaluationCompleted} event. Enforces the case-creation contract
 * pinned by ADR-004 §4.4 (default {@code scoreThreshold=70}) and the
 * {@code processed_events} ledger (ADR-003 §3.3.1) idempotency contract.
 */
@Service
public class CreateCaseFromEvaluationUseCase {

    public static final String IDEMPOTENCY_CONSUMER = "core-backend.case-events";
    public static final String EVENT_TYPE = "FraudEvaluationCompleted";
    public static final String BLOCK_RECOMMENDATION = "BLOCK";

    private static final Logger log = LoggerFactory.getLogger(CreateCaseFromEvaluationUseCase.class);

    private final CaseRepository caseRepository;
    private final IdempotencyService idempotencyService;
    private final int scoreThreshold;

    public CreateCaseFromEvaluationUseCase(
            CaseRepository caseRepository,
            IdempotencyService idempotencyService,
            @Value("${centinela.cases.score-threshold:70}") int scoreThreshold) {
        this.caseRepository = caseRepository;
        this.idempotencyService = idempotencyService;
        if (scoreThreshold < 0 || scoreThreshold > 100) {
            throw new IllegalArgumentException("scoreThreshold must be within [0, 100]");
        }
        this.scoreThreshold = scoreThreshold;
    }

    /**
     * Apply the case-creation decision and persist the resulting row when the
     * event indicates a fraud block.
     *
     * @return {@link Outcome#CREATED} when a new case row was inserted,
     *         {@link Outcome#SKIPPED_THRESHOLD} when the event was below the
     *         threshold (no-op, idempotency row is still inserted to record
     *         the seen aggregateId), or {@link Outcome#DUPLICATE} when the
     *         aggregateId was already processed.
     */
    @Transactional
    public Outcome execute(FraudEvaluationCompletedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        UUID transactionId = event.transactionId();
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        String idempotencyKey = transactionId.toString();

        if (!idempotencyService.tryProcess(IDEMPOTENCY_CONSUMER, idempotencyKey)) {
            log.debug("Skipping duplicate FraudEvaluationCompleted transactionId={}", transactionId);
            return Outcome.DUPLICATE;
        }

        if (caseRepository.existsByTransactionId(transactionId)) {
            log.debug("Case row already exists for transactionId={}", transactionId);
            return Outcome.SKIPPED_THRESHOLD;
        }

        if (!shouldCreateCase(event)) {
            log.debug("FraudEvaluationCompleted transactionId={} below threshold (recommendation={}, score={})",
                    transactionId, event.recommendation(), event.score());
            return Outcome.SKIPPED_THRESHOLD;
        }

        Case newCase = new Case(
                UUID.randomUUID(),
                transactionId,
                event.accountId() == null ? "" : event.accountId(),
                CaseStatus.OPEN,
                clamp(event.score()),
                event.recommendation() == null ? BLOCK_RECOMMENDATION : event.recommendation(),
                event.triggeredRules() == null ? List.of() : event.triggeredRules(),
                event.eventTime() == null ? Instant.now() : event.eventTime(),
                null,
                null,
                null,
                event.correlationId() == null ? transactionId : event.correlationId(),
                event.traceId());

        caseRepository.save(newCase);
        log.info("Created case transactionId={} recommendation={} score={}",
                transactionId, newCase.recommendation(), newCase.score());
        return Outcome.CREATED;
    }

    private boolean shouldCreateCase(FraudEvaluationCompletedEvent event) {
        if (BLOCK_RECOMMENDATION.equalsIgnoreCase(event.recommendation())) {
            return true;
        }
        return event.score() >= scoreThreshold;
    }

    public int scoreThreshold() {
        return scoreThreshold;
    }

    private static int clamp(int score) {
        if (score < 0) {
            return 0;
        }
        return Math.min(score, 100);
    }

    public enum Outcome {
        CREATED,
        SKIPPED_THRESHOLD,
        DUPLICATE
    }

    public record FraudEvaluationCompletedEvent(
            String eventType,
            String eventVersion,
            Instant eventTime,
            UUID correlationId,
            UUID transactionId,
            String accountId,
            String recommendation,
            int score,
            List<Map<String, Object>> triggeredRules,
            String traceId) {
    }
}
