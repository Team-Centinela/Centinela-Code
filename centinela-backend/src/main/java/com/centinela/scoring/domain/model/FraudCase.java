package com.centinela.scoring.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public class FraudCase {
    private String caseId;
    private String transactionId;
    private String cuentaId;
    private BigDecimal score;
    private BigDecimal umbral;
    private String estado;
    private Instant fechaApertura;
    private Instant fechaResolucion;
    private String analistaId;
    private String explicacion;

    public FraudCase() {
        this.estado = "ABIERTO";
        this.fechaApertura = Instant.now();
    }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
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
    public Instant getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(Instant fechaApertura) { this.fechaApertura = fechaApertura; }
    public Instant getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(Instant fechaResolucion) { this.fechaResolucion = fechaResolucion; }
    public String getAnalistaId() { return analistaId; }
    public void setAnalistaId(String analistaId) { this.analistaId = analistaId; }
    public String getExplicacion() { return explicacion; }
    public void setExplicacion(String explicacion) { this.explicacion = explicacion; }
}
