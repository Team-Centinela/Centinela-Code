package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.port.TransactionStatisticsRepository;

import java.time.Instant;

/**
 * Placeholder {@link TransactionStatisticsRepository} that always returns 0.
 * {@code FR-1 Velocity} therefore never fires in this configuration because
 * {@code count &gt; threshold} is false.
 *
 * <p>TODO(@3105jero): replace with the JPA query that reads
 * {@code COUNT(*)} from {@code oltp.transactions} partitioned by
 * {@code account_id} (hash partition per ADR-002 + #17).</p>
 */
public class InMemoryTransactionStatisticsRepository implements TransactionStatisticsRepository {

    @Override
    public long countByAccountIdSince(String accountId, Instant since) {
        return 0L;
    }
}
