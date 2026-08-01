package com.centinela.scoring.infrastructure.api;

import com.centinela.scoring.application.service.ScoringService;
import com.centinela.scoring.domain.model.FraudCase;
import com.centinela.scoring.domain.port.CaseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/scoring")
public class ScoringController {

    private final ScoringService scoringService;
    private final CaseRepository caseRepository;

    public ScoringController(ScoringService scoringService, CaseRepository caseRepository) {
        this.scoringService = scoringService;
        this.caseRepository = caseRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "scoring-engine",
                "version", "1.0.0"
        ));
    }
}
