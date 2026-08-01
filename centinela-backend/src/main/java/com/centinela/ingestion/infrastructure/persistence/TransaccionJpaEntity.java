package com.centinela.ingestion.infrastructure.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transacciones")
public class TransaccionJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    private String transactionId;

    @Column(name = "cuenta_id", nullable = false, length = 64)
    private String cuentaId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(name = "marca_tiempo", nullable = false)
    private Instant marcaTiempo;

    @Column(name = "ubicacion_lat")
    private Double ubicacionLat;

    @Column(name = "ubicacion_lon")
    private Double ubicacionLon;

    @Column(name = "comercio_id", length = 128)
    private String comercioId;

    @Column(name = "comercio_categoria", length = 128)
    private String comercioCategoria;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @Column(nullable = false)
    private Boolean marcada = false;

    @Column(name = "fecha_evaluacion")
    private Instant fechaEvaluacion;

    @Column(name = "reglas_activadas")
    @JdbcTypeCode(SqlTypes.JSON)
    private String reglasActivadas;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Boolean getMarcada() { return marcada; }
    public void setMarcada(Boolean marcada) { this.marcada = marcada; }
    public Instant getFechaEvaluacion() { return fechaEvaluacion; }
    public void setFechaEvaluacion(Instant fechaEvaluacion) { this.fechaEvaluacion = fechaEvaluacion; }
    public String getReglasActivadas() { return reglasActivadas; }
    public void setReglasActivadas(String reglasActivadas) { this.reglasActivadas = reglasActivadas; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
