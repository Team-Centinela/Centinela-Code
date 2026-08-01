package com.centinela.scoring.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionHistory {
    private String transactionId;
    private String cuentaId;
    private BigDecimal monto;
    private Instant marcaTiempo;
    private Double ubicacionLat;
    private Double ubicacionLon;
    private String comercioId;

    public TransactionHistory() {}

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public Instant getMarcaTiempo() { return marcaTiempo; }
    public void setMarcaTiempo(Instant marcaTiempo) { this.marcaTiempo = marcaTiempo; }
    public Double getUbicacionLat() { return ubicacionLat; }
    public void setUbicacionLat(Double ubicacionLat) { this.ubicacionLat = ubicacionLat; }
    public Double getUbicacionLon() { return ubicacionLon; }
    public void setUbicacionLon(Double ubicacionLon) { this.ubicacionLon = ubicacionLon; }
    public String getComercioId() { return comercioId; }
    public void setComercioId(String comercioId) { this.comercioId = comercioId; }
}
