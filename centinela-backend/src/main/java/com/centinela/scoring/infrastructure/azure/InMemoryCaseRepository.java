package com.centinela.scoring.infrastructure.azure;

import com.centinela.scoring.domain.model.FraudCase;
import com.centinela.scoring.domain.port.CaseRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("local")
public class InMemoryCaseRepository implements CaseRepository {

    private final Map<String, FraudCase> cases = new ConcurrentHashMap<>();

    @Override
    public FraudCase save(FraudCase fraudCase) {
        cases.put(fraudCase.getCaseId(), fraudCase);
        return fraudCase;
    }

    @Override
    public FraudCase findByTransactionId(String transactionId) {
        return cases.values().stream()
                .filter(c -> c.getTransactionId().equals(transactionId))
                .findFirst()
                .orElse(null);
    }

    public Map<String, FraudCase> getAllCases() {
        return cases;
    }
}
