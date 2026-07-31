package com.centinela.cases.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataCaseRepository extends JpaRepository<CaseEntity, UUID> {

    boolean existsByTransactionId(UUID transactionId);
}
