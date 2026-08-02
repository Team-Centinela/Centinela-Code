package com.centinela.serverless.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity backing {@code rules_config.rule_configs} (Flyway V1__init_engine_schemas.sql).
 * One row per rule code (FR-1..FR-4 + PIPELINE + AGGREGATOR). Per-rule tunables
 * (score_value, params JSONB, enabled flag) live here so analysts can adjust
 * thresholds without a redeploy — ADR-004 §4.4 + #270.
 *
 * <p>The {@code config} JSON column is mapped to {@code Map<String, Object>}
 * via {@link RuleConfigConverter} instead of Hibernate 6's built-in
 * {@code @JdbcTypeCode(SqlTypes.JSON)} because the latter, paired with the
 * engine's custom {@code USE_BIG_DECIMAL_FOR_FLOATS} Jackson configuration,
 * mis-handles the JSON-as-VARCHAR return type from H2 (MODE=PostgreSQL).
 * The converter keeps the JSON round-trip explicit and portable.</p>
 */
@Entity
@Table(name = "rule_configs", schema = "rules_config")
public class RuleConfigEntity {

    @Id
    private UUID id;

    @Column(name = "rule_code", nullable = false, length = 50, unique = true)
    private String ruleCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Convert(converter = RuleConfigConverter.class)
    @Column(name = "config", nullable = false)
    private Map<String, Object> config;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RuleConfigEntity() {}

    public RuleConfigEntity(UUID id, String ruleCode, boolean enabled,
                            Map<String, Object> config, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.ruleCode = ruleCode;
        this.enabled = enabled;
        this.config = config;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public String ruleCode() { return ruleCode; }
    public boolean enabled() { return enabled; }
    public Map<String, Object> config() { return config; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}