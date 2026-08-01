package com.centinela.ingestion.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Transaccion {
    private final String id;
    private final String cuentaId;
    private final BigDecimal monto;
    private final String moneda;
    private final Instant marcaTiempo;
    private final Coordenada ubicacion;
    private final String comercioId;
    private final String comercioCategoria;
    private final Instant fechaCreacion;

    public Transaccion(String cuentaId, BigDecimal monto, String moneda,
                       Instant marcaTiempo, Coordenada ubicacion,
                       String comercioId, String comercioCategoria) {
        this.id = UUID.randomUUID().toString();
        this.cuentaId = Objects.requireNonNull(cuentaId, "cuentaId no puede ser null");
        this.monto = Objects.requireNonNull(monto, "monto no puede ser null");
        this.moneda = moneda != null ? moneda : "USD";
        this.marcaTiempo = marcaTiempo != null ? marcaTiempo : Instant.now();
        this.ubicacion = ubicacion;
        this.comercioId = comercioId;
        this.comercioCategoria = comercioCategoria;
        this.fechaCreacion = Instant.now();
    }

    private Transaccion(Builder builder) {
        this.id = builder.id;
        this.cuentaId = builder.cuentaId;
        this.monto = builder.monto;
        this.moneda = builder.moneda;
        this.marcaTiempo = builder.marcaTiempo;
        this.ubicacion = builder.ubicacion;
        this.comercioId = builder.comercioId;
        this.comercioCategoria = builder.comercioCategoria;
        this.fechaCreacion = builder.fechaCreacion;
    }

    public String getId() { return id; }
    public String getCuentaId() { return cuentaId; }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public Instant getMarcaTiempo() { return marcaTiempo; }
    public Coordenada getUbicacion() { return ubicacion; }
    public String getComercioId() { return comercioId; }
    public String getComercioCategoria() { return comercioCategoria; }
    public Instant getFechaCreacion() { return fechaCreacion; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String cuentaId;
        private BigDecimal monto;
        private String moneda = "USD";
        private Instant marcaTiempo = Instant.now();
        private Coordenada ubicacion;
        private String comercioId;
        private String comercioCategoria;
        private Instant fechaCreacion = Instant.now();

        public Builder id(String id) { this.id = id; return this; }
        public Builder cuentaId(String cuentaId) { this.cuentaId = cuentaId; return this; }
        public Builder monto(BigDecimal monto) { this.monto = monto; return this; }
        public Builder moneda(String moneda) { this.moneda = moneda; return this; }
        public Builder marcaTiempo(Instant marcaTiempo) { this.marcaTiempo = marcaTiempo; return this; }
        public Builder ubicacion(Coordenada ubicacion) { this.ubicacion = ubicacion; return this; }
        public Builder comercioId(String comercioId) { this.comercioId = comercioId; return this; }
        public Builder comercioCategoria(String comercioCategoria) { this.comercioCategoria = comercioCategoria; return this; }
        public Builder fechaCreacion(Instant fechaCreacion) { this.fechaCreacion = fechaCreacion; return this; }

        public Transaccion build() {
            if (this.id == null) {
                this.id = UUID.randomUUID().toString();
            }
            return new Transaccion(this);
        }
    }
}
