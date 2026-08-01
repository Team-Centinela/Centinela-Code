package com.centinela.scoring.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScoredTransaction {
    private String transactionId;
    private String cuentaId;
    private BigDecimal monto;
    private Instant marcaTiempo;
    private BigDecimal score;
    private BigDecimal umbral;
    private boolean marcada;
    private List<RuleActivation> reglasActivadas;
    private Instant fechaEvaluacion;

    public ScoredTransaction() {
        this.reglasActivadas = new ArrayList<>();
        this.score = BigDecimal.ZERO;
        this.fechaEvaluacion = Instant.now();
    }

    public void agregarActivacion(RuleActivation activacion) {
        this.reglasActivadas.add(activacion);
        this.score = this.score.add(activacion.getPuntos());
    }

    public boolean superaUmbral() {
        return this.score.compareTo(this.umbral) > 0;
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public Instant getMarcaTiempo() { return marcaTiempo; }
    public void setMarcaTiempo(Instant marcaTiempo) { this.marcaTiempo = marcaTiempo; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public BigDecimal getUmbral() { return umbral; }
    public void setUmbral(BigDecimal umbral) { this.umbral = umbral; }
    public boolean isMarcada() { return marcada; }
    public void setMarcada(boolean marcada) { this.marcada = marcada; }
    public List<RuleActivation> getReglasActivadas() { return reglasActivadas; }
    public void setReglasActivadas(List<RuleActivation> reglasActivadas) { this.reglasActivadas = reglasActivadas; }
    public Instant getFechaEvaluacion() { return fechaEvaluacion; }
    public void setFechaEvaluacion(Instant fechaEvaluacion) { this.fechaEvaluacion = fechaEvaluacion; }
}
