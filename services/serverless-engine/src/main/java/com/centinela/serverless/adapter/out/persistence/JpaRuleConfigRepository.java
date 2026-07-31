package com.centinela.serverless.adapter.out.persistence;

import com.centinela.serverless.domain.port.RuleConfig;
import com.centinela.serverless.domain.port.RuleConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA-backed implementation of {@link RuleConfigRepository}.
 *
 * <p>Reads from {@code rules_config.rule_configs} (Flyway V1__init_engine_schemas.sql).
 * Replaces the previous in-memory placeholder (#270 — configurable threshold
 * DB-backed). The PIPELINE row carries the canonical {@code scoreThreshold}
 * (ADR-004 §4.4); the AGGREGATOR row carries the soft {@code flagThreshold};
 * the FR-1..FR-4 rows carry each rule's {@code scoreValue} + tunable params.</p>
 *
 * <p>Seeded by {@code V3__seed_rule_configs.sql}. Fail-closed on a missing
 * row at startup per ADR-004 §4.4 — the {@code @Bean int scoreThreshold} /
 * {@code flagThreshold} wiring falls back to {@code FraudPipeline.DEFAULT_*}
 * constants and logs a {@code RULE_CONFIG_MISSING} event so analysts see
 * the drift instead of a silent in-memory default.</p>
 *
 * <p>The {@code config} JSON column is stored as a String in the entity (see
 * {@link RuleConfigEntity#config()} for the H2 MODE=PostgreSQL rationale) and
 * parsed into {@code Map<String, Object>} here for the domain
 * {@link RuleConfig} record. Jackson's stock {@code Map<String, Object>} type
 * reference is reused; empty / blank strings yield an empty map so a missing
 * {@code config} body does not NPE the {@code RuleConfig} constructor.</p>
 */
@Repository
@Transactional
public class JpaRuleConfigRepository implements RuleConfigRepository {

    private final SpringDataRuleConfigRepository springRepo;

    public JpaRuleConfigRepository(SpringDataRuleConfigRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Optional<RuleConfig> findByRuleCode(String ruleCode) {
        return springRepo.findByRuleCode(ruleCode)
                .map(e -> new RuleConfig(e.ruleCode(), e.enabled(), e.config()));
    }
}