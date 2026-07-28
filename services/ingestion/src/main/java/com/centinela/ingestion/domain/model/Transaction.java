package com.centinela.ingestion.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class Transaction {
    private final TransactionId id;
    private final AccountId accountId;
    private final Money amount;
    private final MerchantId merchantId;
    private final GeoLocation location;
    private final Instant timestamp;

    public Transaction(TransactionId id, AccountId accountId, Money amount,
                       MerchantId merchantId, GeoLocation location, Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.merchantId = merchantId;
        this.location = location;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public TransactionId id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money amount() {
        return amount;
    }

    public MerchantId merchantId() {
        return merchantId;
    }

    public GeoLocation geoLocation() {
        return location;
    }

    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", amount=" + amount +
                '}';
    }
}
