package com.centinela.scoring.domain.model;

import java.math.BigDecimal;
import java.util.Map;

public class RuleActivation {
    private String ruleId;
    private String ruleName;
    private BigDecimal puntos;
    private Map<String, Object> datosActivacion;
    private String descripcion;

    public RuleActivation() {}

    public RuleActivation(String ruleId, String ruleName, BigDecimal puntos,
                          Map<String, Object> datosActivacion, String descripcion) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.puntos = puntos;
        this.datosActivacion = datosActivacion;
        this.descripcion = descripcion;
    }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public BigDecimal getPuntos() { return puntos; }
    public void setPuntos(BigDecimal puntos) { this.puntos = puntos; }
    public Map<String, Object> getDatosActivacion() { return datosActivacion; }
    public void setDatosActivacion(Map<String, Object> datosActivacion) { this.datosActivacion = datosActivacion; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
