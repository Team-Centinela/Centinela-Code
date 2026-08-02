package com.centinela.cases.adapter.out.persistence;

import com.centinela.cases.domain.model.Case;
import com.centinela.cases.domain.model.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cases", schema = "cases")
public class CaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, length = 255)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CaseStatus status;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "recommendation", nullable = false, length = 20)
    private String recommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", nullable = false)
    private List<Map<String, Object>> triggeredRules;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "trace_id", length = 32)
    private String traceId;

    protected CaseEntity() {
    }

    public CaseEntity(UUID id, UUID transactionId, String accountId, CaseStatus status, int score,
                      String recommendation, List<Map<String, Object>> triggeredRules, Instant openedAt,
                      Instant resolvedAt, String resolvedBy, String resolutionNotes,
                      UUID correlationId, String traceId) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.status = status;
        this.score = score;
        this.recommendation = recommendation;
        this.triggeredRules = triggeredRules;
        this.openedAt = openedAt;
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = resolutionNotes;
        this.correlationId = correlationId;
        this.traceId = traceId;
    }

    public static CaseEntity fromDomain(Case domain) {
        return new CaseEntity(
                domain.id(),
                domain.transactionId(),
                domain.accountId(),
                domain.status(),
                domain.score(),
                domain.recommendation(),
                domain.triggeredRules(),
                domain.openedAt(),
                domain.resolvedAt(),
                domain.resolvedBy(),
                domain.resolutionNotes(),
                domain.correlationId(),
                domain.traceId());
    }

    public Case toDomain() {
        return new Case(
                id,
                transactionId,
                accountId,
                status,
                score,
                recommendation,
                triggeredRules == null ? List.of() : triggeredRules,
                openedAt,
                resolvedAt,
                resolvedBy,
                resolutionNotes,
                correlationId,
                traceId);
    }

    public UUID id() { return id; }
    public UUID transactionId() { return transactionId; }
    public String accountId() { return accountId; }
    public CaseStatus status() { return status; }
    public int score() { return score; }
    public String recommendation() { return recommendation; }
    public List<Map<String, Object>> triggeredRules() { return triggeredRules; }
    public Instant openedAt() { return openedAt; }
    public Instant resolvedAt() { return resolvedAt; }
    public String resolvedBy() { return resolvedBy; }
    public String resolutionNotes() { return resolutionNotes; }
    public UUID correlationId() { return correlationId; }
    public String traceId() { return traceId; }
}
