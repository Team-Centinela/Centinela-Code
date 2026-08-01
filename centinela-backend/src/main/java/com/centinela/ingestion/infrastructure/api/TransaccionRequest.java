package com.centinela.ingestion.infrastructure.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TransaccionRequest {

    @NotBlank(message = "El cuentaId no puede estar vacio")
    private String cuentaId;

    @NotNull(message = "El monto no puede estar vacio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    private BigDecimal monto;

    private String moneda;

    private Long marcaTiempo;

    private Double ubicacionLat;

    private Double ubicacionLon;

    private String comercioId;

    private String comercioCategoria;

    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public Long getMarcaTiempo() { return marcaTiempo; }
    public void setMarcaTiempo(Long marcaTiempo) { this.marcaTiempo = marcaTiempo; }
    public Double getUbicacionLat() { return ubicacionLat; }
    public void setUbicacionLat(Double ubicacionLat) { this.ubicacionLat = ubicacionLat; }
    public Double getUbicacionLon() { return ubicacionLon; }
    public void setUbicacionLon(Double ubicacionLon) { this.ubicacionLon = ubicacionLon; }
    public String getComercioId() { return comercioId; }
    public void setComercioId(String comercioId) { this.comercioId = comercioId; }
    public String getComercioCategoria() { return comercioCategoria; }
    public void setComercioCategoria(String comercioCategoria) { this.comercioCategoria = comercioCategoria; }
}
