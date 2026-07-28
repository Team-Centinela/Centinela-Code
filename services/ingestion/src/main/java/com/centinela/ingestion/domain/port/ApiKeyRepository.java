package com.centinela.ingestion.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository {
    Optional<UUID> resolveByHash(String sha256hash);
}
