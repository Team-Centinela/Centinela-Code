package com.centinela.scoring.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudCaseJpaRepository extends JpaRepository<FraudCaseJpaEntity, String> {

    List<FraudCaseJpaEntity> findByEstadoOrderByFechaAperturaDesc(String estado);

    List<FraudCaseJpaEntity> findByCuentaIdOrderByFechaAperturaDesc(String cuentaId);

    List<FraudCaseJpaEntity> findByAnalistaIdAndEstadoNot(String analistaId, String estado);

    FraudCaseJpaEntity findByTransactionId(String transactionId);

    @Query("SELECT fc.estado, COUNT(fc) FROM FraudCaseJpaEntity fc GROUP BY fc.estado")
    List<Object[]> countByEstado();

    long countByEstado(String estado);
}
