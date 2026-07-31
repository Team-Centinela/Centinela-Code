package com.centinela.cases.adapter.in.observability;

import com.centinela.cases.application.CreateCaseFromEvaluationUseCase;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that surfaces the result of the most recent
 * {@code case-events} consumer round-trip at {@code /actuator/health/cases}.
 * The use case tracks aggregate dedup + case-creation counters internally so
 * the indicator can answer without an extra DB round-trip.
 */
@Component("cases")
public class CasesHealthIndicator implements HealthIndicator {

    private final CreateCaseFromEvaluationUseCase useCase;

    public CasesHealthIndicator(CreateCaseFromEvaluationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("status", "UP")
                .withDetail("scoreThreshold", useCase.scoreThreshold())
                .build();
    }
}
