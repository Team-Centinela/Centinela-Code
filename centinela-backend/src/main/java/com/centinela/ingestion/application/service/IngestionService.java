package com.centinela.ingestion.application.service;

import com.centinela.ingestion.domain.model.Transaccion;
import com.centinela.ingestion.domain.port.TransaccionRepository;
import com.centinela.scoring.application.service.ScoringService;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.ScoringRepository;
import com.centinela.shared.events.DomainEvent;
import com.centinela.shared.events.EventPublisher;
import com.centinela.shared.events.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TransaccionRepository repository;
    private final EventPublisher eventPublisher;
    private final Optional<ScoringService> scoringService;
    private final Optional<ScoringRepository> scoringRepository;

    public IngestionService(TransaccionRepository repository, EventPublisher eventPublisher,
                            Optional<ScoringService> scoringService,
                            Optional<ScoringRepository> scoringRepository) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.scoringService = scoringService;
        this.scoringRepository = scoringRepository;
    }

    public Transaccion procesarTransaccion(Transaccion transaccion) {
        log.info("Procesando transaccion {} de cuenta {}", transaccion.getId(), transaccion.getCuentaId());

        Transaccion guardada = repository.save(transaccion);

        DomainEvent event = new DomainEvent(EventTypes.TRANSACTION_RECEIVED, Map.of(
                "transactionId", guardada.getId(),
                "cuentaId", guardada.getCuentaId(),
                "monto", guardada.getMonto().toPlainString(),
                "moneda", guardada.getMoneda(),
                "marcaTiempo", guardada.getMarcaTiempo().toString(),
                "ubicacionLat", guardada.getUbicacion() != null ? guardada.getUbicacion().getLatitud() : 0,
                "ubicacionLon", guardada.getUbicacion() != null ? guardada.getUbicacion().getLongitud() : 0,
                "comercioId", guardada.getComercioId() != null ? guardada.getComercioId() : "",
                "comercioCategoria", guardada.getComercioCategoria() != null ? guardada.getComercioCategoria() : ""
        ));
        eventPublisher.publishSafe(event);

        scoringService.ifPresent(ss -> {
            try {
                TransactionHistory th = new TransactionHistory();
                th.setTransactionId(guardada.getId());
                th.setCuentaId(guardada.getCuentaId());
                th.setMonto(guardada.getMonto());
                th.setMarcaTiempo(guardada.getMarcaTiempo());
                if (guardada.getUbicacion() != null) {
                    th.setUbicacionLat(guardada.getUbicacion().getLatitud());
                    th.setUbicacionLon(guardada.getUbicacion().getLongitud());
                }
                th.setComercioId(guardada.getComercioId());
                th.setComercioCategoria(guardada.getComercioCategoria());

                scoringRepository.ifPresent(repo -> repo.save(th));

                ss.evaluarTransaccion(th);
            } catch (Exception e) {
                log.error("Error en scoring directo: {}", e.getMessage());
            }
        });

        log.info("Transaccion {} persistida y evento publicado", guardada.getId());
        return guardada;
    }

    public Optional<Transaccion> obtenerTransaccion(String id) {
        return repository.findById(id);
    }

    public List<Transaccion> obtenerTransaccionesCuenta(String cuentaId) {
        return repository.findByCuentaId(cuentaId);
    }

    public List<Transaccion> obtenerTransaccionesRecientes(String cuentaId, int limit) {
        return repository.findRecentByCuentaId(cuentaId, limit);
    }

    public List<Transaccion> obtenerTodas() {
        return repository.findAll();
    }
}
