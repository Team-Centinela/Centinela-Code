package com.centinela.corebackend.transaction.domain.event;

import com.centinela.corebackend.transaction.domain.model.TransactionId;

import java.time.Instant;

public record TransactionReceivedEvent(TransactionId transactionId, String accountId, Instant occurredAt) {

    public TransactionReceivedEvent(TransactionId transactionId, String accountId) {
        this(transactionId, accountId, Instant.now());
    }
}
