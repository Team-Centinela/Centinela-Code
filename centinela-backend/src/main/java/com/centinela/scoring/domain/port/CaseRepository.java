package com.centinela.scoring.domain.port;

import com.centinela.scoring.domain.model.FraudCase;

import java.util.List;
import java.util.Optional;

public interface CaseRepository {
    FraudCase save(FraudCase fraudCase);
    Optional<FraudCase> findById(String id);
    FraudCase findByTransactionId(String transactionId);
    List<FraudCase> findByCuentaId(String cuentaId);
    List<FraudCase> findByEstado(String estado);
    List<FraudCase> findAll();
}
