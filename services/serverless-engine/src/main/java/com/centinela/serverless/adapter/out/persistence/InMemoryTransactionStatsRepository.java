package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.port.TransactionStats;
import com.centinela.serverless.domain.port.TransactionStatsRepository;

import java.util.Optional;

/**
 * Placeholder {@link TransactionStatsRepository} returning empty for every
 * lookup. {@code FR-2 Atypical Amount} therefore never fires in this
 * configuration because {@code statsRepo.findByAccountId(...)} is empty
 * and the rule short-circuits.
 *
 * <p>TODO(@3105jero): replace with the JPA query reading
 * {@code AVG(amount)} + sample size + standard deviation from
 * {@code oltp.transactions} partitioned by {@code account_id}.</p>
 */
public class InMemoryTransactionStatsRepository implements TransactionStatsRepository {

    @Override
    public Optional<TransactionStats> findByAccountId(String accountId) {
        return Optional.empty();
    }
}
