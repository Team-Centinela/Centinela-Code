package com.centinela.scoring.infrastructure.azure;

import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.ScoringRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Profile("local")
public class InMemoryScoringRepository implements ScoringRepository {

    private final Map<String, List<TransactionHistory>> store = new ConcurrentHashMap<>();

    @Override
    public List<TransactionHistory> findRecentByCuentaId(String cuentaId, Instant desde) {
        return store.getOrDefault(cuentaId, Collections.emptyList()).stream()
                .filter(t -> t.getMarcaTiempo().isAfter(desde))
                .sorted(Comparator.comparing(TransactionHistory::getMarcaTiempo).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TransactionHistory> findLastByCuentaId(String cuentaId) {
        return store.getOrDefault(cuentaId, Collections.emptyList()).stream()
                .max(Comparator.comparing(TransactionHistory::getMarcaTiempo));
    }

    @Override
    public BigDecimal calculateAverageMonto(String cuentaId) {
        List<TransactionHistory> transactions = store.getOrDefault(cuentaId, Collections.emptyList());
        if (transactions.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = transactions.stream()
                .map(TransactionHistory::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(transactions.size()), 2, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public long countRecentByCuentaId(String cuentaId, Instant desde) {
        return store.getOrDefault(cuentaId, Collections.emptyList()).stream()
                .filter(t -> t.getMarcaTiempo().isAfter(desde))
                .count();
    }

    public void save(TransactionHistory transaction) {
        store.computeIfAbsent(transaction.getCuentaId(), k -> new ArrayList<>()).add(transaction);
    }
}
