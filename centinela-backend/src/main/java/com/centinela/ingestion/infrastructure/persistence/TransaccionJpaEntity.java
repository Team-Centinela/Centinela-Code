package com.centinela.ingestion.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transacciones")
public class TransaccionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String cuentaId;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private String moneda;

    @Column(nullable = false)
    private Instant marcaTiempo;

    private Double ubicacionLat;
    private Double ubicacionLon;

    private String comercioId;
    private String comercioCategoria;

    @Column(nullable = false)
    private Instant fechaCreacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public Instant getMarcaTiempo() { return marcaTiempo; }
    public void setMarcaTiempo(Instant marcaTiempo) { this.marcaTiempo = marcaTiempo; }
    public Double getUbicacionLat() { return ubicacionLat; }
    public void setUbicacionLat(Double ubicacionLat) { this.ubicacionLat = ubicacionLat; }
    public Double getUbicacionLon() { return ubicacionLon; }
    public void setUbicacionLon(Double ubicacionLon) { this.ubicacionLon = ubicacionLon; }
    public String getComercioId() { return comercioId; }
    public void setComercioId(String comercioId) { this.comercioId = comercioId; }
    public String getComercioCategoria() { return comercioCategoria; }
    public void setComercioCategoria(String comercioCategoria) { this.comercioCategoria = comercioCategoria; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
