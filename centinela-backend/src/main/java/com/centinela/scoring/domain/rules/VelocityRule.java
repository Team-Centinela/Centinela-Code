package com.centinela.scoring.domain.rules;

import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class VelocityRule implements Rule {

    @Value("${centinela.rules.velocity.window-minutes:5}")
    private int windowMinutes;

    @Value("${centinela.rules.velocity.max-transactions:3}")
    private int maxTransactions;

    @Value("${centinela.rules.velocity.points:35}")
    private int points;

    @Override
    public String getRuleId() { return "VELOCITY"; }

    @Override
    public String getRuleName() { return "Velocidad de transaccion"; }

    @Override
    public int getPoints() { return points; }

    @Override
    public RuleActivation evaluate(TransactionHistory current, List<TransactionHistory> history) {
        Instant windowStart = current.getMarcaTiempo().minus(Duration.ofMinutes(windowMinutes));

        long count = history.stream()
                .filter(t -> t.getMarcaTiempo().isAfter(windowStart))
                .filter(t -> !t.getTransactionId().equals(current.getTransactionId()))
                .count();

        if (count >= maxTransactions) {
            Map<String, Object> datos = new HashMap<>();
            datos.put("transacciones_en_ventana", count);
            datos.put("ventana_minutos", windowMinutes);
            datos.put("limite", maxTransactions);

            String descripcion = String.format(
                    "Se detectaron %d transacciones de esta cuenta en los ultimos %d minutos, " +
                    "cuando el limite es de %d (+%d puntos)",
                    count, windowMinutes, maxTransactions, points);

            return new RuleActivation(getRuleId(), getRuleName(), BigDecimal.valueOf(points), datos, descripcion);
        }

        return null;
    }
}
