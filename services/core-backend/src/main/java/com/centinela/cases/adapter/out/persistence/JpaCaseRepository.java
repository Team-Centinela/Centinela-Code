package com.centinela.cases.adapter.out.persistence;

import com.centinela.cases.domain.model.Case;
import com.centinela.cases.domain.port.CaseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class JpaCaseRepository implements CaseRepository {

    private final SpringDataCaseRepository springRepo;

    public JpaCaseRepository(SpringDataCaseRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @Transactional
    public Case save(Case fraudCase) {
        CaseEntity entity = CaseEntity.fromDomain(fraudCase);
        CaseEntity saved = springRepo.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTransactionId(UUID transactionId) {
        return springRepo.existsByTransactionId(transactionId);
    }
}
