package com.centinela.serverless.application.rules;

import com.centinela.serverless.domain.model.TransactionMessage;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleEvidenceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * FR-3 Impossible Geo — PostGIS ST_DWithin on the previous transaction location.
 *
 * <p>Owner: @3105jero (logic placeholder). The actual {@code ST_DWithin} call is
 * implemented in {@code JpaRuleEvidenceRepository}; this rule just calls the port
 * and shapes the evidence payload.
 */
@Component
public class Fr3ImpossibleGeoRule implements RuleStage {

    private final RuleEvidenceRepository repository;
    private final double maxKmPerHour;

    public Fr3ImpossibleGeoRule(RuleEvidenceRepository repository,
                                 @org.springframework.beans.factory.annotation.Value("${fraud.rule.fr3.max-km-per-hour:900.0}") double maxKmPerHour) {
        this.repository = repository;
        this.maxKmPerHour = maxKmPerHour;
    }

    @Override
    public String ruleCode() {
        return "FR-3";
    }

    @Override
    public TriggeredRule evaluate(TransactionMessage tx) {
        if (!tx.hasCoordinates()) {
            return TriggeredRule.notFired(ruleCode());
        }
        Optional<RuleEvidenceRepository.TransactionLocation> previous = repository.previousLocationFor(tx);
        if (previous.isEmpty()) {
            return TriggeredRule.notFired(ruleCode());
        }
        RuleEvidenceRepository.TransactionLocation prev = previous.get();
        double distanceKm = haversineKm(prev.latitude(), prev.longitude(), tx.latitude(), tx.longitude());
        double elapsedSeconds = java.time.Duration.between(prev.timestamp(), tx.timestamp()).getSeconds();
        if (elapsedSeconds <= 0d) {
            elapsedSeconds = 1d;
        }
        double kmPerHour = distanceKm / (elapsedSeconds / 3600d);
        if (kmPerHour <= maxKmPerHour) {
            return TriggeredRule.notFired(ruleCode());
        }
        double score = Math.min(50.0d, Math.max(20.0d, (kmPerHour / maxKmPerHour) * 20.0d));
        Map<String, Object> evidence = Map.of(
                "distanceKm", distanceKm,
                "elapsedSeconds", elapsedSeconds,
                "kmPerHour", kmPerHour,
                "limitKmPerHour", maxKmPerHour
        );
        return TriggeredRule.fired(ruleCode(), score, evidence);
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthKm = 6371.0088d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthKm * c;
    }
}
