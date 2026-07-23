package com.centinela.ingestion.shared.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventJpaRepository repository;

    public IdempotencyService(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean tryProcess(String consumer, String idempotencyKey) {
        int inserted = repository.tryInsert(consumer, idempotencyKey);
        if (inserted == 1) {
            log.debug("First-time processing for consumer={} key={}", consumer, idempotencyKey);
            return true;
        }
        log.debug("Duplicate detection for consumer={} key={} — skipped", consumer, idempotencyKey);
        return false;
    }
}
