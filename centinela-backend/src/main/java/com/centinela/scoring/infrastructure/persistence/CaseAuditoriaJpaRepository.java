package com.centinela.scoring.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseAuditoriaJpaRepository extends JpaRepository<CaseAuditoriaJpaEntity, String> {

    List<CaseAuditoriaJpaEntity> findByFraudCaseIdOrderByFechaDesc(String caseId);
}
