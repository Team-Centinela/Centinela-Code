package com.centinela.scoring.domain.port;

import com.centinela.scoring.domain.model.FraudCase;

public interface CaseRepository {
    FraudCase save(FraudCase fraudCase);
    FraudCase findByTransactionId(String transactionId);
}
