package com.centinela.scoring.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fraud_cases")
public class FraudCaseJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "cuenta_id", nullable = false, length = 64)
    private String cuentaId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal umbral;

    @Column(nullable = false, length = 32)
    private String estado = "ABIERTO";

    @Column(columnDefinition = "TEXT")
    private String explicacion;

    @Column(name = "fecha_apertura", nullable = false)
    private Instant fechaApertura = Instant.now();

    @Column(name = "fecha_resolucion")
    private Instant fechaResolucion;

    @Column(name = "analista_id", length = 64)
    private String analistaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "fraudCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CaseAuditoriaJpaEntity> auditoria = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getUmbral() { return umbral; }
    public void setUmbral(BigDecimal umbral) { this.umbral = umbral; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getExplicacion() { return explicacion; }
    public void setExplicacion(String explicacion) { this.explicacion = explicacion; }
    public Instant getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(Instant fechaApertura) { this.fechaApertura = fechaApertura; }
    public Instant getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(Instant fechaResolucion) { this.fechaResolucion = fechaResolucion; }
    public String getAnalistaId() { return analistaId; }
    public void setAnalistaId(String analistaId) { this.analistaId = analistaId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<CaseAuditoriaJpaEntity> getAuditoria() { return auditoria; }
    public void setAuditoria(List<CaseAuditoriaJpaEntity> auditoria) { this.auditoria = auditoria; }
}
