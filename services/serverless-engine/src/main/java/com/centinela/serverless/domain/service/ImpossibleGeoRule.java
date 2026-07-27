package com.centinela.serverless.domain.service;

import com.centinela.serverless.domain.event.TransactionReceivedEvent;
import com.centinela.serverless.domain.model.TriggeredRule;
import com.centinela.serverless.domain.port.RuleConfigRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ImpossibleGeoRule implements PipelineStage {

    private static final String RULE_CODE = "FR-3";
    static final double DEFAULT_MAX_SPEED_KMPH = 800.0;
    static final int DEFAULT_SCORE = 25;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RuleConfigRepository configRepo;

    public ImpossibleGeoRule(RuleConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public Optional<TriggeredRule> evaluate(EvaluationContext ctx) {
        TransactionReceivedEvent current = ctx.sourceTransaction();
        TransactionReceivedEvent previous = ctx.previousTransaction();

        if (previous == null) return Optional.empty();
        if (noLocation(current) || noLocation(previous)) return Optional.empty();

        long timeDeltaMinutes = ChronoUnit.MINUTES.between(previous.timestamp(), current.timestamp());
        if (timeDeltaMinutes <= 0) return Optional.empty();

        double distanceKm = haversine(
                previous.latitude(), previous.longitude(),
                current.latitude(), current.longitude()
        );

        var cfg = loadConfig();
        double maxPossibleKm = cfg.maxSpeedKmph * (timeDeltaMinutes / 60.0);

        if (distanceKm > maxPossibleKm) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("currentLocation", Map.of("lat", current.latitude(), "lng", current.longitude()));
            evidence.put("previousLocation", Map.of("lat", previous.latitude(), "lng", previous.longitude()));
            evidence.put("distanceKm", round1(distanceKm));
            evidence.put("timeDeltaMinutes", timeDeltaMinutes);
            evidence.put("maxPossibleKm", round1(maxPossibleKm));
            evidence.put("physicallyImpossible", true);
            return Optional.of(new TriggeredRule(RULE_CODE, cfg.score, evidence, Instant.now()));
        }
        return Optional.empty();
    }

    private static boolean noLocation(TransactionReceivedEvent tx) {
        return tx.latitude() == null || tx.longitude() == null;
    }

    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Config loadConfig() {
        var opt = configRepo.findByRuleCode(RULE_CODE);
        if (opt.isEmpty() || !opt.get().enabled()) {
            return new Config(DEFAULT_MAX_SPEED_KMPH, DEFAULT_SCORE);
        }
        var cfg = opt.get();
        return new Config(
                cfg.get("maxSpeedKmph", DEFAULT_MAX_SPEED_KMPH),
                cfg.get("score", DEFAULT_SCORE)
        );
    }

    private record Config(double maxSpeedKmph, int score) {}
}
