package com.centinela.corebackend.account.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Transfer {

    private final AccountId fromAccountId;
    private final AccountId toAccountId;
    private final BigDecimal amount;
    private final String description;
    private final Instant timestamp;

    public Transfer(AccountId fromAccountId, AccountId toAccountId, BigDecimal amount, String description) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.description = description;
        this.timestamp = Instant.now();
    }

    public AccountId getFromAccountId() { return fromAccountId; }
    public AccountId getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}
