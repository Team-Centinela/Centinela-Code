package com.centinela.scoring;

import com.centinela.scoring.application.service.ScoringService;
import com.centinela.scoring.domain.model.RuleActivation;
import com.centinela.scoring.domain.model.ScoredTransaction;
import com.centinela.scoring.domain.model.TransactionHistory;
import com.centinela.scoring.domain.port.CaseRepository;
import com.centinela.scoring.domain.port.Rule;
import com.centinela.scoring.domain.port.ScoringRepository;
import com.centinela.shared.events.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock
    private ScoringRepository scoringRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private Rule velocityRule;

    @InjectMocks
    private ScoringService scoringService;

    private TransactionHistory transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionHistory();
        transaction.setTransactionId("txn-001");
        transaction.setCuentaId("cuenta-001");
        transaction.setMonto(new BigDecimal("50000.00"));
        transaction.setMarcaTiempo(Instant.now());

        when(scoringRepository.findRecentByCuentaId(any(), any())).thenReturn(Collections.emptyList());
        when(velocityRule.getRuleId()).thenReturn("VELOCITY");
        when(velocityRule.evaluate(any(), any())).thenReturn(null);
    }

    @Test
    void shouldScoreTransactionBelowThreshold() {
        ScoredTransaction result = scoringService.evaluarTransaccion(transaction);

        assertNotNull(result);
        assertFalse(result.isMarcada());
        assertEquals("txn-001", result.getTransactionId());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void shouldMarkTransactionAboveThreshold() {
        RuleActivation activation = new RuleActivation();
        activation.setRuleId("VELOCITY");
        activation.setPuntos(new BigDecimal("35"));
        activation.setDescripcion("Test activation");

        when(velocityRule.evaluate(any(), any())).thenReturn(activation);

        ScoredTransaction result = scoringService.evaluarTransaccion(transaction);

        assertTrue(result.isMarcada());
        assertEquals(new BigDecimal("35"), result.getScore());
        verify(caseRepository).save(any());
    }

    @Test
    void shouldNotCreateCaseWhenBelowThreshold() {
        scoringService.evaluarTransaccion(transaction);

        verify(caseRepository, never()).save(any());
    }
}
