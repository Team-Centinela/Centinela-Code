package com.centinela.ingestion.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransaccionJpaEntityRepository extends JpaRepository<TransaccionJpaEntity, Long> {
    Optional<TransaccionJpaEntity> findByTransactionId(String transactionId);
    List<TransaccionJpaEntity> findByCuentaIdOrderByMarcaTiempoDesc(String cuentaId);

    @Query("SELECT t FROM TransaccionJpaEntity t WHERE t.cuentaId = :cuentaId ORDER BY t.marcaTiempo DESC")
    List<TransaccionJpaEntity> findTopNByCuentaIdOrderByMarcaTiempoDesc(
            @Param("cuentaId") String cuentaId,
            org.springframework.data.domain.Pageable pageable);

    default List<TransaccionJpaEntity> findTopNByCuentaIdOrderByMarcaTiempoDesc(String cuentaId, int limit) {
        return findTopNByCuentaIdOrderByMarcaTiempoDesc(
                cuentaId, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
