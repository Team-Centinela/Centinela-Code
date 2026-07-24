package com.centinela.serverless.domain.port;

import java.util.Optional;

public interface TransactionStatsRepository {
    Optional<TransactionStats> findByAccountId(String accountId);
}
