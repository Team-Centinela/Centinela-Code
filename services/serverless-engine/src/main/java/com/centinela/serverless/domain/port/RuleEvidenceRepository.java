package com.centinela.serverless.domain.port;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;

public interface RuleEvidenceRepository {
    long countRecentForAccount(TransactionMessage tx);

    boolean isHighRiskMerchant(String merchantId);

    double getAverageAmount(String accountId);

    java.util.Optional<TransactionLocation> previousLocationFor(TransactionMessage tx);

    record TransactionLocation(double latitude, double longitude, java.time.Instant timestamp) {
    }
}
