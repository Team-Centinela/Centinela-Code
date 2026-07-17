package com.centinela.corebackend.transaction.infrastructure.persistence;

import com.centinela.corebackend.transaction.domain.model.Transaction;
import com.centinela.corebackend.transaction.domain.model.TransactionId;
import com.centinela.corebackend.transaction.domain.port.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public JpaTransactionRepository(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Transaction transaction) {
        jpaRepository.save(TransactionMapper.toEntity(transaction));
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return jpaRepository.findById(id.value()).map(TransactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findByAccountId(String accountId) {
        return jpaRepository.findByAccountIdOrderByTimestampDesc(accountId)
                .stream()
                .map(TransactionMapper::toDomain)
                .toList();
    }
}
