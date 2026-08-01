package com.centinela.scoring.domain.rules;

import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AmountRule implements Rule {

    @Value("${centinela.rules.amount.multiplier-threshold:10}")
    private int multiplierThreshold;

    @Value("${centinela.rules.amount.points:30}")
    private int points;

    @Override
    public String getRuleId() { return "AMOUNT"; }

    @Override
    public String getRuleName() { return "Monto atipico"; }

    @Override
    public int getPoints() { return points; }

    @Override
    public RuleActivation evaluate(TransactionHistory current, List<TransactionHistory> history) {
        if (history.isEmpty()) {
            return null;
        }

        BigDecimal totalHistorico = history.stream()
                .map(TransactionHistory::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal promedio = totalHistorico.divide(
                BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);

        if (promedio.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal ratio = current.getMonto().divide(promedio, 2, RoundingMode.HALF_UP);

        if (ratio.compareTo(BigDecimal.valueOf(multiplierThreshold)) >= 0) {
            Map<String, Object> datos = new HashMap<>();
            datos.put("monto_actual", current.getMonto().toPlainString());
            datos.put("promedio_historico", promedio.toPlainString());
            datos.put("ratio", ratio.toPlainString());
            datos.put("limite_ratio", multiplierThreshold);

            String descripcion = String.format(
                    "El monto de $%s supera en %dx el promedio historico de la cuenta ($%s) (+%d puntos)",
                    current.getMonto().toPlainString(), ratio.intValue(), promedio.toPlainString(), points);

            return new RuleActivation(getRuleId(), getRuleName(), BigDecimal.valueOf(points), datos, descripcion);
        }

        return null;
    }
}
