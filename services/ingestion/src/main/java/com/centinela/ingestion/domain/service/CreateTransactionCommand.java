package com.centinela.ingestion.domain.service;

import com.centinela.ingestion.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class CreateTransactionCommand {
    private final AccountId accountId;
    private final BigDecimal amount;
    private final String currency;
    private final String merchantId;
    private final Double latitude;
    private final Double longitude;
    private final Instant timestamp;

    public CreateTransactionCommand(AccountId accountId, BigDecimal amount, String currency,
                                    String merchantId, Double latitude, Double longitude,
                                    Instant timestamp) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public AccountId accountId() {
        return accountId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String merchantId() {
        return merchantId;
    }

    public Double latitude() {
        return latitude;
    }

    public Double longitude() {
        return longitude;
    }

    public Instant timestamp() {
        return timestamp;
    }
}
