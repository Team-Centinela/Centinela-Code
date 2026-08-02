package com.centinela.cases.domain.port;

import com.centinela.cases.domain.model.Case;

import java.util.UUID;

public interface CaseRepository {

    Case save(Case fraudCase);

    boolean existsByTransactionId(UUID transactionId);
}
