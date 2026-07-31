package com.centinela.cases.application;

import com.centinela.cases.domain.model.Case;
import com.centinela.cases.domain.model.CaseStatus;
import com.centinela.cases.domain.port.CaseRepository;
import com.centinela.cases.application.CreateCaseFromEvaluationUseCase.FraudEvaluationCompletedEvent;
import com.centinela.shared.messaging.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateCaseFromEvaluationUseCaseTest {

    private static final int SCORE_THRESHOLD = 70;

    private CaseRepository caseRepository;
    private IdempotencyService idempotencyService;
    private CreateCaseFromEvaluationUseCase useCase;

    @BeforeEach
    void setUp() {
        caseRepository = mock(CaseRepository.class);
        idempotencyService = mock(IdempotencyService.class);
        when(idempotencyService.tryProcess(anyString(), anyString())).thenReturn(true);
        when(caseRepository.existsByTransactionId(any())).thenReturn(false);
        useCase = new CreateCaseFromEvaluationUseCase(caseRepository, idempotencyService, SCORE_THRESHOLD);
    }

    @Test
    void blockRecommendationAlwaysCreatesCase() {
        FraudEvaluationCompletedEvent event = sampleEvent("BLOCK", 30);
        when(caseRepository.save(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCaseFromEvaluationUseCase.Outcome outcome = useCase.execute(event);

        assertThat(outcome).isEqualTo(CreateCaseFromEvaluationUseCase.Outcome.CREATED);
        ArgumentCaptor<Case> captor = ArgumentCaptor.forClass(Case.class);
        verify(caseRepository).save(captor.capture());
        Case saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(CaseStatus.OPEN);
        assertThat(saved.recommendation()).isEqualTo("BLOCK");
        assertThat(saved.transactionId()).isEqualTo(event.transactionId());
    }

    @Test
    void scoreAtThresholdCreatesCase() {
        FraudEvaluationCompletedEvent event = sampleEvent("FLAG", SCORE_THRESHOLD);
        when(caseRepository.save(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCaseFromEvaluationUseCase.Outcome outcome = useCase.execute(event);

        assertThat(outcome).isEqualTo(CreateCaseFromEvaluationUseCase.Outcome.CREATED);
        verify(caseRepository).save(any(Case.class));
    }

    @Test
    void scoreBelowThresholdWithoutBlockRecommendationIsSkipped() {
        FraudEvaluationCompletedEvent event = sampleEvent("APPROVE", 20);

        CreateCaseFromEvaluationUseCase.Outcome outcome = useCase.execute(event);

        assertThat(outcome).isEqualTo(CreateCaseFromEvaluationUseCase.Outcome.SKIPPED_THRESHOLD);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void duplicateAggregateIdIsSkipped() {
        when(idempotencyService.tryProcess(eq(CreateCaseFromEvaluationUseCase.IDEMPOTENCY_CONSUMER), anyString()))
                .thenReturn(false);
        FraudEvaluationCompletedEvent event = sampleEvent("BLOCK", 100);

        CreateCaseFromEvaluationUseCase.Outcome outcome = useCase.execute(event);

        assertThat(outcome).isEqualTo(CreateCaseFromEvaluationUseCase.Outcome.DUPLICATE);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void scoreIsClampedToValidRange() {
        FraudEvaluationCompletedEvent event = sampleEvent("FLAG", 250);
        when(caseRepository.save(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event);

        ArgumentCaptor<Case> captor = ArgumentCaptor.forClass(Case.class);
        verify(caseRepository).save(captor.capture());
        assertThat(captor.getValue().score()).isEqualTo(100);
    }

    @Test
    void invalidThresholdRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CreateCaseFromEvaluationUseCase(caseRepository, idempotencyService, -1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CreateCaseFromEvaluationUseCase(caseRepository, idempotencyService, 101));
    }

    private static FraudEvaluationCompletedEvent sampleEvent(String recommendation, int score) {
        return new FraudEvaluationCompletedEvent(
                "FraudEvaluationCompleted",
                "1.0",
                Instant.parse("2026-07-31T12:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acct-42",
                recommendation,
                score,
                List.of(Map.of("ruleCode", "FR-1", "score", score, "rawEvidence", Map.of())),
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
    }
}
