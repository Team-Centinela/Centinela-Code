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

    @org.springframework.beans.factory.annotation.Value("${app.idempotency.schema:oltp}")
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
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Outcome claim(String consumer, String messageId, UUID transactionId) {
        List<ReceivedMessageRepository.Row> rows = repository.lockForUpdateSkipping(schema, messageId, consumer);
        if (!rows.isEmpty()) {
            ReceivedMessageRepository.Row existing = rows.get(0);
            if ("PROCESSED".equals(existing.status())) {
                log.debug("Duplicate detection for consumer={} key={} — already PROCESSED, skipping",
                        consumer, messageId);
                return Outcome.DUPLICATE_DONE;
            }
            log.debug("Duplicate detection for consumer={} key={} — row is RECEIVED but was "
                    + "skipped because another worker holds the lock", consumer, messageId);
            return Outcome.INFLIGHT_OTHER;
        }

        ReceivedMessageRepository.Row inserted = repository.insertIfAbsent(schema, messageId, consumer, transactionId);
        if (inserted != null) {
            log.debug("Claimed consumer={} key={} for transaction {}", consumer, messageId, transactionId);
            return Outcome.CLAIMED;
        }
        log.debug("Race lost: consumer={} key={} — concurrent insert won the claim; skipping",
                consumer, messageId);
        return Outcome.RACE_LOST;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReceivedMessageRepository.Row> markProcessed(ReceivedMessageRepository.Row row) {
        repository.markProcessed(schema, row);
        return Optional.of(row);
    }

    public enum Outcome {
        CLAIMED,
        DUPLICATE_DONE,
        INFLIGHT_OTHER,
        RACE_LOST
    }
}
