package com.centinela.scoring.domain.rules;

import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class MerchantRiskRule implements Rule {

    private static final Set<String> HIGH_RISK_MERCHANTS = Set.of(
            "darkmarket", "gambling", "crypto-mixer", "illegal-services"
    );

    private static final Set<String> HIGH_RISK_CATEGORIES = Set.of(
            "gambling", "cryptocurrency", "money-transfer", "adult-content", "weapons"
    );

    @Value("${centinela.rules.merchant.points:20}")
    private int points;

    @Override
    public String getRuleId() { return "MERCHANT_RISK"; }

    @Override
    public String getRuleName() { return "Comercio de riesgo"; }

    @Override
    public int getPoints() { return points; }

    @Override
    public RuleActivation evaluate(TransactionHistory current, List<TransactionHistory> history) {
        boolean merchantRisk = current.getComercioId() != null &&
                HIGH_RISK_MERCHANTS.contains(current.getComercioId().toLowerCase());

        boolean categoryRisk = current.getComercioId() != null &&
                HIGH_RISK_CATEGORIES.contains(current.getComercioId().toLowerCase());

        if (merchantRisk || categoryRisk) {
            Map<String, Object> datos = new HashMap<>();
            datos.put("comercio_id", current.getComercioId());
            datos.put("tipo_riesgo", merchantRisk ? "comercio" : "categoria");

            String descripcion = String.format(
                    "La transaccion va hacia un comercio o categoria marcada como sospechosa: %s (+%d puntos)",
                    current.getComercioId(), points);

            return new RuleActivation(getRuleId(), getRuleName(), BigDecimal.valueOf(points), datos, descripcion);
        }

        return null;
    }
}
