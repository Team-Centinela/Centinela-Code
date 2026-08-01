package com.centinela.scoring.infrastructure.api;

import com.centinela.scoring.domain.model.FraudCase;
import com.centinela.scoring.domain.port.CaseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseRepository caseRepository;

    public CaseController(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @GetMapping
    public ResponseEntity<List<FraudCase>> getAllCases() {
        return ResponseEntity.ok(caseRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCaseById(@PathVariable String id) {
        return caseRepository.findById(id)
                .map(c -> ResponseEntity.ok((Object) c))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cuenta/{cuentaId}")
    public ResponseEntity<List<FraudCase>> getCasesByCuentaId(@PathVariable String cuentaId) {
        return ResponseEntity.ok(caseRepository.findByCuentaId(cuentaId));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<FraudCase>> getCasesByEstado(@PathVariable String estado) {
        return ResponseEntity.ok(caseRepository.findByEstado(estado));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "cases-api",
                "version", "1.0.0"
        ));
    }
}
