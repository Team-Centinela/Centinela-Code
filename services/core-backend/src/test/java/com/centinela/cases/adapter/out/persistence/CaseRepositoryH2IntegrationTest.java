package com.centinela.cases.adapter.out.persistence;

import com.centinela.cases.domain.model.Case;
import com.centinela.cases.domain.model.CaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository round-trip for {@link JpaCaseRepository} backed by the
 * H2 in-memory database declared in {@code application-h2.yml}. Verifies the
 * domain → entity → JPA → domain mapping for the {@code cases.cases} table
 * that V4 creates.
 */
@SpringBootTest(classes = CaseRepositoryH2IntegrationTest.TestApp.class)
@ActiveProfiles("h2")
class CaseRepositoryH2IntegrationTest {

    @Autowired
    private JpaCaseRepository caseRepository;

    @Test
    void savesAndReadsBackCaseRow() {
        Case domain = new Case(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acct-99",
                CaseStatus.OPEN,
                85,
                "BLOCK",
                List.of(Map.of("ruleCode", "FR-1", "score", 30)),
                Instant.parse("2026-07-31T10:00:00Z"),
                null,
                null,
                null,
                UUID.randomUUID(),
                "0af7651916cd43dd8448eb211c80319c");

        Case saved = caseRepository.save(domain);

        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(caseRepository.existsByTransactionId(domain.transactionId())).isTrue();
        assertThat(caseRepository.existsByTransactionId(UUID.randomUUID())).isFalse();
    }

    @SpringBootApplication
    @EnableJpaRepositories(basePackageClasses = SpringDataCaseRepository.class)
    @EntityScan(basePackageClasses = CaseEntity.class)
    static class TestApp {
    }
}
