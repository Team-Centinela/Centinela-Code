package com.centinela.serverless.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionMessage(
        UUID transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        double latitude,
        double longitude,
        String type,
        String merchantId,
        String description
) {
    public boolean hasCoordinates() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }
}
