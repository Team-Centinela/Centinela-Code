package com.centinela.ingestion.application.service;

import com.centinela.ingestion.domain.model.Transaccion;
import com.centinela.ingestion.domain.port.TransaccionRepository;
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

    public IngestionService(TransaccionRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
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
}
