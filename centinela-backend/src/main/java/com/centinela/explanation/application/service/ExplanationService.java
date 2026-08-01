package com.centinela.explanation.application.service;

import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.ScoredTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExplanationService {

    private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);

    public String generateExplanation(ScoredTransaction scored) {
        log.info("Generando explicacion para transaccion {}", scored.getTransactionId());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Transaccion marcada con score %s (umbral: %s).%n",
                scored.getScore().toPlainString(), scored.getUmbral().toPlainString()));
        sb.append("\n");

        List<RuleActivation> activations = scored.getReglasActivadas();
        if (activations.isEmpty()) {
            sb.append("No se activaron reglas de deteccion.\n");
            return sb.toString();
        }

        sb.append(String.format("Se detectaron %d reglas activadas:%n%n", activations.size()));

        for (int i = 0; i < activations.size(); i++) {
            RuleActivation activation = activations.get(i);
            sb.append(String.format("%d. %s%n", i + 1, activation.getDescripcion()));
            sb.append(String.format("   Datos: %s%n%n", activation.getDatosActivacion()));
        }

        return sb.toString();
    }
}
