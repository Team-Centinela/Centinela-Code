package com.centinela.serverless.domain.port;

import java.time.Instant;

public interface TransactionStatisticsRepository {
    long countByAccountIdSince(String accountId, Instant since);
}
