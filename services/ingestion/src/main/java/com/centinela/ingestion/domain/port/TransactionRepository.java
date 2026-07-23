package com.centinela.ingestion.domain.port;

import com.centinela.ingestion.domain.model.Transaction;
import com.centinela.ingestion.domain.model.TransactionId;

import java.util.Optional;

public interface TransactionRepository {
    void save(Transaction tx);

    Optional<Transaction> findById(TransactionId id);
}
