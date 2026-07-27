package com.centinela.ingestion.infrastructure.persistence;

import com.centinela.ingestion.domain.model.Transaction;
import com.centinela.ingestion.domain.model.TransactionId;
import com.centinela.ingestion.domain.port.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaTransactionRepository implements TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public JpaTransactionRepository(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Transaction tx) {
        jpaRepository.save(TransactionMapper.toEntity(tx));
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return jpaRepository.findById(id.value())
                .map(TransactionMapper::toDomain);
    }
}
