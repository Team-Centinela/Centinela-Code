package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.TriggeredRuleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class JpaTriggeredRuleRepository implements TriggeredRuleRepository {

    private final SpringDataTriggeredRuleRepository springRepo;

    public JpaTriggeredRuleRepository(SpringDataTriggeredRuleRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void saveAll(List<TriggeredRule> rules, UUID transactionId) {
        List<TriggeredRuleEntity> entities = rules.stream()
                .map(r -> new TriggeredRuleEntity(
                        UUID.randomUUID(),
                        transactionId,
                        r.ruleCode(),
                        r.score(),
                        r.rawEvidence(),
                        r.evaluatedAt() != null ? r.evaluatedAt() : Instant.now()
                ))
                .toList();
        springRepo.saveAll(entities);
    }

    @Override
    public List<TriggeredRule> findByTransactionId(UUID transactionId) {
        return springRepo.findByTransactionId(transactionId).stream()
                .map(e -> new TriggeredRule(
                        e.ruleCode(),
                        e.score(),
                        e.rawEvidence(),
                        e.evaluatedAt()
                ))
                .toList();
    }
}
