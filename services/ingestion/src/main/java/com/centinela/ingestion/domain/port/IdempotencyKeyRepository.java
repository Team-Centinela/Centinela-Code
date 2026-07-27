package com.centinela.ingestion.domain.port;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository {
    Optional<IdempotencyRecord> findByKeyHash(String keyHash);

    void save(IdempotencyRecord record);

    int deleteOlderThan(Instant cutoff);
}
