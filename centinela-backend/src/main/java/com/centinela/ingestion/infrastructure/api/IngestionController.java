package com.centinela.ingestion.infrastructure.api;

import com.centinela.ingestion.application.service.IngestionService;
import com.centinela.ingestion.domain.model.Coordenada;
import com.centinela.ingestion.domain.model.Transaccion;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transacciones")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<TransaccionResponse> recibirTransaccion(
            @Valid @RequestBody TransaccionRequest request) {

        log.info("Recibida transaccion de cuenta: {}", request.getCuentaId());

        Coordenada ubicacion = null;
        if (request.getUbicacionLat() != null && request.getUbicacionLon() != null) {
            ubicacion = new Coordenada(request.getUbicacionLat(), request.getUbicacionLon());
        }

        Instant marcaTiempo = request.getMarcaTiempo() != null
                ? Instant.ofEpochMilli(request.getMarcaTiempo())
                : Instant.now();

        Transaccion transaccion = Transaccion.builder()
                .cuentaId(request.getCuentaId())
                .monto(request.getMonto())
                .moneda(request.getMoneda())
                .marcaTiempo(marcaTiempo)
                .ubicacion(ubicacion)
                .comercioId(request.getComercioId())
                .comercioCategoria(request.getComercioCategoria())
                .build();

        Transaccion procesada = ingestionService.procesarTransaccion(transaccion);

        TransaccionResponse response = new TransaccionResponse(
                procesada.getId(),
                procesada.getCuentaId(),
                "ACCEPTED",
                "Transaccion recibida y en proceso de analisis"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerTransaccion(@PathVariable String id) {
        return ingestionService.obtenerTransaccion(id)
                .map(t -> ResponseEntity.ok((Object) t))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cuenta/{cuentaId}")
    public ResponseEntity<?> obtenerTransaccionesCuenta(@PathVariable String cuentaId) {
        return ResponseEntity.ok(ingestionService.obtenerTransaccionesCuenta(cuentaId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ingestion-api",
                "version", "1.0.0"
        ));
    }
}
