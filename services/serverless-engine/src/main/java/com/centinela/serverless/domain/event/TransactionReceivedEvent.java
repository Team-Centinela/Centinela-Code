package com.centinela.serverless.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionReceivedEvent(
        UUID transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        Double latitude,
        Double longitude,
        Instant timestamp
) {
    public TransactionReceivedEvent {
        if (transactionId == null) throw new IllegalArgumentException("transactionId must not be null");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId must not be blank");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
    }
}
