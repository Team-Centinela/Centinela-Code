package com.centinela.serverless.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReceivedMessageIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(ReceivedMessageIdempotencyService.class);

    private final ReceivedMessageRepository repository;

    private String schema = "oltp";

    public ReceivedMessageIdempotencyService(ReceivedMessageRepository repository) {
        this.repository = repository;
    }

    @org.springframework.beans.factory.annotation.Value("${app.idempotency.schema:received_messages}")
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * ADR-003 §3.3.2 — Business status gating over {@code received_messages}.
     *
     * <p>Sequence inside one transaction:</p>
     * <ol>
     *   <li>{@code SELECT ... FOR UPDATE SKIP LOCKED} attempts a locked read of an existing
     *       row for {@code (consumer, messageId)}.</li>
     *   <li>If the row exists with status PROCESSED → duplicate already evaluated; ACK & skip.</li>
     *   <li>If the row exists with status RECEIVED but is locked by another instance,
     *       {@code SKIP LOCKED} returns no row → ACK & skip (another worker will finish).</li>
     *   <li>If no row exists → insert with {@code INSERT ... ON CONFLICT DO NOTHING}. A
     *       return code of 1 wins the claim; 0 means a concurrent insert won and we must skip.</li>
     * </ol>
     *
     * <p>Returned row invariants:</p>
     * <ul>
     *   <li>{@code ClaimResult.outcome() == CLAIMED} ⇒ {@code ClaimResult.row()} is non-null
     *       and is the freshly inserted row, ready to be passed to {@link #markProcessed(ReceivedMessageRepository.Row)}
     *       when the surrounding business work commits.</li>
     *   <li>Any other outcome ⇒ {@code ClaimResult.row()} is {@code null}.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ClaimResult claim(String consumer, String messageId, UUID transactionId) {
        List<ReceivedMessageRepository.Row> rows = repository.lockForUpdateSkipping(schema, messageId, consumer);
        if (!rows.isEmpty()) {
            ReceivedMessageRepository.Row existing = rows.get(0);
            if ("PROCESSED".equals(existing.status())) {
                log.debug("Duplicate detection for consumer={} key={} — already PROCESSED, skipping",
                        consumer, messageId);
                return new ClaimResult(Outcome.DUPLICATE_DONE, null);
            }
            log.debug("Duplicate detection for consumer={} key={} — row is RECEIVED but was "
                    + "skipped because another worker holds the lock", consumer, messageId);
            return new ClaimResult(Outcome.INFLIGHT_OTHER, null);
        }

        ReceivedMessageRepository.Row inserted = repository.insertIfAbsent(schema, messageId, consumer, transactionId);
        if (inserted != null) {
            log.debug("Claimed consumer={} key={} for transaction {}", consumer, messageId, transactionId);
            return new ClaimResult(Outcome.CLAIMED, inserted);
        }
        log.debug("Race lost: consumer={} key={} — concurrent insert won the claim; skipping",
                consumer, messageId);
        return new ClaimResult(Outcome.RACE_LOST, null);
    }

    /**
     * Advance the {@code received_messages} row from {@code RECEIVED} to {@code PROCESSED}
     * in the caller's transaction. Must be called from a transactional context
     * ({@code Propagation.MANDATORY}); fails fast otherwise so a misconfigured caller
     * cannot create an orphaned PROCESSED row.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(ReceivedMessageRepository.Row row) {
        if (row == null) {
            throw new IllegalArgumentException("row must not be null");
        }
        repository.markProcessed(schema, row);
    }

    public enum Outcome {
        CLAIMED,
        DUPLICATE_DONE,
        INFLIGHT_OTHER,
        RACE_LOST
    }

    public record ClaimResult(Outcome outcome, ReceivedMessageRepository.Row row) {
        public boolean claimed() {
            return outcome == Outcome.CLAIMED;
        }
    }
}
