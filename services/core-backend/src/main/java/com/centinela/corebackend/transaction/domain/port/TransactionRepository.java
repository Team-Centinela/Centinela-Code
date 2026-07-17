package com.centinela.corebackend.transaction.domain.port;

import com.centinela.corebackend.transaction.domain.model.Transaction;
import com.centinela.corebackend.transaction.domain.model.TransactionId;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(TransactionId id);

    List<Transaction> findByAccountId(String accountId);
}
