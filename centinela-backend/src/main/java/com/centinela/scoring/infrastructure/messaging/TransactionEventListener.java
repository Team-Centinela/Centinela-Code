package com.centinela.scoring.infrastructure.messaging;

import com.centinela.scoring.application.service.ScoringService;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.infrastructure.azure.InMemoryScoringRepository;
import com.centinela.shared.events.DomainEvent;
import com.centinela.shared.events.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final ScoringService scoringService;
    private final InMemoryScoringRepository scoringRepository;

    public TransactionEventListener(ScoringService scoringService,
                                    InMemoryScoringRepository scoringRepository) {
        this.scoringService = scoringService;
        this.scoringRepository = scoringRepository;
    }

    @EventListener
    public void handleTransactionReceived(DomainEvent event) {
        if (!EventTypes.TRANSACTION_RECEIVED.equals(event.getEventType())) {
            return;
        }

        log.info("Evento recibido: {} para transaccion {}", event.getEventType(), event.getPayload().get("transactionId"));

        Map<String, Object> payload = event.getPayload();

        TransactionHistory history = new TransactionHistory();
        history.setTransactionId((String) payload.get("transactionId"));
        history.setCuentaId((String) payload.get("cuentaId"));
        history.setMonto(new BigDecimal((String) payload.get("monto")));
        history.setMarcaTiempo(Instant.parse((String) payload.get("marcaTiempo")));
        history.setUbicacionLat((Double) payload.get("ubicacionLat"));
        history.setUbicacionLon((Double) payload.get("ubicacionLon"));
        history.setComercioId((String) payload.get("comercioId"));

        // Save to scoring repository for history queries
        scoringRepository.save(history);

        scoringService.evaluarTransaccion(history);
    }
}
