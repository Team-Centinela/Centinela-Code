package com.centinela.scoring.domain.port;

import com.centinela.scoring.domain.model.TransactionHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScoringRepository {
    void save(TransactionHistory transaction);
    List<TransactionHistory> findRecentByCuentaId(String cuentaId, Instant desde);
    Optional<TransactionHistory> findLastByCuentaId(String cuentaId);
    BigDecimal calculateAverageMonto(String cuentaId);
    long countRecentByCuentaId(String cuentaId, Instant desde);
}
