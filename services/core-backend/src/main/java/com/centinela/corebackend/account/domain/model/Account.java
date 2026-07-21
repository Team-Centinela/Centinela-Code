package com.centinela.corebackend.account.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public class Account {

    private final AccountId id;
    private final String owner;
    private final Currency currency;
    private BigDecimal balance;
    private final Instant createdAt;
    private Long version;

    public Account(AccountId id, String owner, Currency currency, BigDecimal initialBalance) {
        this(id, owner, currency, initialBalance, Instant.now(), null);
    }

    public Account(AccountId id, String owner, Currency currency, BigDecimal balance, Instant createdAt) {
        this(id, owner, currency, balance, createdAt, null);
    }

    public Account(AccountId id, String owner, Currency currency, BigDecimal balance, Instant createdAt, Long version) {
        this.id = id;
        this.owner = owner;
        this.currency = currency;
        this.balance = balance;
        this.createdAt = createdAt;
        this.version = version;
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(id, balance, amount);
        }
        this.balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = balance.add(amount);
    }

    public AccountId getId() { return id; }
    public String getOwner() { return owner; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
