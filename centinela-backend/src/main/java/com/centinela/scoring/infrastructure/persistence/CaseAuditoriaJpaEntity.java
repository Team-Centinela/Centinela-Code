package com.centinela.scoring.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_auditoria")
public class CaseAuditoriaJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private FraudCaseJpaEntity fraudCase;

    @Column(nullable = false, length = 64)
    private String accion;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(nullable = false, length = 64)
    private String usuario;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public FraudCaseJpaEntity getFraudCase() { return fraudCase; }
    public void setFraudCase(FraudCaseJpaEntity fraudCase) { this.fraudCase = fraudCase; }
    public String getAccion() { return accion; }
    public void setAccion(String accion) { this.accion = accion; }
    public String getValorAnterior() { return valorAnterior; }
    public void setValorAnterior(String valorAnterior) { this.valorAnterior = valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public void setValorNuevo(String valorNuevo) { this.valorNuevo = valorNuevo; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public Instant getFecha() { return fecha; }
    public void setFecha(Instant fecha) { this.fecha = fecha; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
