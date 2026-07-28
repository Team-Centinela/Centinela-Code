package com.centinela.serverless.domain.port;

import java.time.Instant;

public record FlaggedMerchant(
        String merchantId,
        String riskCategory,
        Instant flaggedAt,
        String reason
) {
    public FlaggedMerchant {
        if (merchantId == null || merchantId.isBlank()) throw new IllegalArgumentException("merchantId must not be blank");
        if (riskCategory == null || riskCategory.isBlank()) throw new IllegalArgumentException("riskCategory must not be blank");
        if (flaggedAt == null) throw new IllegalArgumentException("flaggedAt must not be null");
    }
}
