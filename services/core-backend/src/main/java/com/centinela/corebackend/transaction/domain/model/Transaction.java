package com.centinela.corebackend.transaction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public class Transaction {

    private final TransactionId id;
    private final String accountId;
    private final Money amount;
    private final Instant timestamp;
    private final GeoLocation location;
    private final TransactionType type;
    private final String merchantId;
    private final String description;
    private TransactionStatus status;

    public Transaction(TransactionId id, String accountId, Money amount, Instant timestamp,
                       GeoLocation location, TransactionType type, String merchantId,
                       String description) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.timestamp = timestamp;
        this.location = location;
        this.type = type;
        this.merchantId = merchantId;
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    public TransactionId getId() { return id; }
    public String getAccountId() { return accountId; }
    public Money getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
    public GeoLocation getLocation() { return location; }
    public TransactionType getType() { return type; }
    public String getMerchantId() { return merchantId; }
    public String getDescription() { return description; }
    public TransactionStatus getStatus() { return status; }

    public void markEvaluated() {
        this.status = TransactionStatus.EVALUATED;
    }

    public void markFlagged() {
        this.status = TransactionStatus.FLAGGED;
    }

    public void markCleared() {
        this.status = TransactionStatus.CLEARED;
    }

    public boolean isHighValue() {
        return amount.isGreaterThan(Money.usd(BigDecimal.valueOf(10_000)));
    }
}
