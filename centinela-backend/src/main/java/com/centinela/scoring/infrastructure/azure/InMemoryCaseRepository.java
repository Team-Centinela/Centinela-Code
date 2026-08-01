package com.centinela.scoring.infrastructure.azure;

import com.centinela.scoring.domain.model.FraudCase;
import com.centinela.scoring.domain.port.CaseRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    public Optional<FraudCase> findById(String id) {
        return Optional.ofNullable(cases.get(id));
    }

    @Override
    public FraudCase findByTransactionId(String transactionId) {
        return cases.values().stream()
                .filter(c -> c.getTransactionId().equals(transactionId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<FraudCase> findByCuentaId(String cuentaId) {
        return cases.values().stream()
                .filter(c -> c.getCuentaId().equals(cuentaId))
                .collect(Collectors.toList());
    }

    @Override
    public List<FraudCase> findByEstado(String estado) {
        return cases.values().stream()
                .filter(c -> c.getEstado().equals(estado))
                .collect(Collectors.toList());
    }

    public Map<String, FraudCase> getAllCases() {
        return cases;
    }
}
