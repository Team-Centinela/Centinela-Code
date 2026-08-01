package com.centinela.scoring.application.service;

import com.centinela.scoring.domain.model.*;
import com.centinela.scoring.domain.port.CaseRepository;
import com.centinela.scoring.domain.port.Rule;
import com.centinela.scoring.domain.port.ScoringRepository;
import com.centinela.explanation.application.service.ExplanationService;
import com.centinela.shared.events.DomainEvent;
import com.centinela.shared.events.EventPublisher;
import com.centinela.shared.events.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final ScoringRepository scoringRepository;
    private final CaseRepository caseRepository;
    private final EventPublisher eventPublisher;
    private final ExplanationService explanationService;
    private final List<Rule> rules;

    @Value("${centinela.scoring.threshold:60}")
    private BigDecimal threshold;

    public ScoringService(ScoringRepository scoringRepository,
                          CaseRepository caseRepository,
                          EventPublisher eventPublisher,
                          ExplanationService explanationService,
                          List<Rule> rules) {
        this.scoringRepository = scoringRepository;
        this.caseRepository = caseRepository;
        this.eventPublisher = eventPublisher;
        this.explanationService = explanationService;
        this.rules = rules;
    }

    public ScoredTransaction evaluarTransaccion(TransactionHistory transaccion) {
        log.info("Evaluando transaccion {} de cuenta {}", transaccion.getTransactionId(), transaccion.getCuentaId());

        Instant windowStart = transaccion.getMarcaTiempo().minus(java.time.Duration.ofMinutes(30));
        List<TransactionHistory> history = scoringRepository.findRecentByCuentaId(transaccion.getCuentaId(), windowStart);

        ScoredTransaction scored = new ScoredTransaction();
        scored.setTransactionId(transaccion.getTransactionId());
        scored.setCuentaId(transaccion.getCuentaId());
        scored.setMonto(transaccion.getMonto());
        scored.setMarcaTiempo(transaccion.getMarcaTiempo());
        scored.setUmbral(threshold);

        for (Rule rule : rules) {
            RuleActivation activation = rule.evaluate(transaccion, history);
            if (activation != null) {
                scored.agregarActivacion(activation);
                log.info("Regla {} activada: {} (+{} puntos)",
                        rule.getRuleId(), activation.getDescripcion(), activation.getPuntos());
            }
        }

        scored.setMarcada(scored.superaUmbral());

        if (scored.superaUmbral()) {
            log.info("Transaccion {} MARCADA con score {} (umbral: {})",
                    transaccion.getTransactionId(), scored.getScore(), threshold);

            FraudCase fraudCase = new FraudCase();
            fraudCase.setCaseId(UUID.randomUUID().toString());
            fraudCase.setTransactionId(transaccion.getTransactionId());
            fraudCase.setCuentaId(transaccion.getCuentaId());
            fraudCase.setScore(scored.getScore());
            fraudCase.setUmbral(threshold);
            fraudCase.setExplicacion(explanationService.generateExplanation(scored));

            caseRepository.save(fraudCase);

            eventPublisher.publishSafe(new DomainEvent(EventTypes.CASE_OPENED, Map.of(
                    "caseId", fraudCase.getCaseId(),
                    "transactionId", transaccion.getTransactionId(),
                    "score", scored.getScore().toPlainString(),
                    "reglasActivadas", scored.getReglasActivadas().size()
            )));
        } else {
            log.info("Transaccion {} ACEPTADA con score {} (umbral: {})",
                    transaccion.getTransactionId(), scored.getScore(), threshold);
        }

        eventPublisher.publishSafe(new DomainEvent(EventTypes.TRANSACTION_SCORED, Map.of(
                "transactionId", transaccion.getTransactionId(),
                "score", scored.getScore().toPlainString(),
                "marcada", scored.isMarcada()
        )));

        return scored;
    }
}
