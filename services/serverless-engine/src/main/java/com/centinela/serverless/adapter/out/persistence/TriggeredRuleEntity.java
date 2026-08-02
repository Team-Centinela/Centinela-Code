package com.centinela.serverless.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "triggered_rules", schema = "triggered_rules")
public class TriggeredRuleEntity {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "raw_evidence", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rawEvidence;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected TriggeredRuleEntity() {}

    public TriggeredRuleEntity(UUID id, UUID transactionId, String ruleCode, int score, Map<String, Object> rawEvidence, Instant evaluatedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.ruleCode = ruleCode;
        this.score = score;
        this.rawEvidence = rawEvidence;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID id() { return id; }
    public UUID transactionId() { return transactionId; }
    public String ruleCode() { return ruleCode; }
    public int score() { return score; }
    public Map<String, Object> rawEvidence() { return rawEvidence; }
    public Instant evaluatedAt() { return evaluatedAt; }
}
