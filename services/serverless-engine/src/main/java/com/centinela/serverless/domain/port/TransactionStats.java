package com.centinela.serverless.domain.port;

import java.math.BigDecimal;

public record TransactionStats(
        BigDecimal avg,
        BigDecimal stdDev
) {
    public TransactionStats {
        if (avg == null) throw new IllegalArgumentException("avg must not be null");
        if (stdDev == null) throw new IllegalArgumentException("stdDev must not be null");
    }
}
