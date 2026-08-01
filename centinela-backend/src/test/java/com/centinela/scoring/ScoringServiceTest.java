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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

    @Mock
    private Rule amountRule;

    @InjectMocks
    private ScoringService scoringService;

    private TransactionHistory transaction;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scoringService, "threshold", new BigDecimal("60"));
        List<Rule> rules = new ArrayList<>();
        rules.add(velocityRule);
        rules.add(amountRule);
        ReflectionTestUtils.setField(scoringService, "rules", rules);

        transaction = new TransactionHistory();
        transaction.setTransactionId("txn-001");
        transaction.setCuentaId("cuenta-001");
        transaction.setMonto(new BigDecimal("50000.00"));
        transaction.setMarcaTiempo(Instant.now());

        lenient().when(scoringRepository.findRecentByCuentaId(any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldScoreTransactionBelowThreshold() {
        lenient().when(velocityRule.evaluate(any(), any())).thenReturn(null);
        lenient().when(amountRule.evaluate(any(), any())).thenReturn(null);

        ScoredTransaction result = scoringService.evaluarTransaccion(transaction);

        assertNotNull(result);
        assertFalse(result.isMarcada());
        assertEquals("txn-001", result.getTransactionId());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void shouldMarkTransactionAboveThreshold() {
        RuleActivation velocityActivation = new RuleActivation();
        velocityActivation.setRuleId("VELOCITY");
        velocityActivation.setPuntos(new BigDecimal("35"));
        velocityActivation.setDescripcion("3 transacciones en 5 minutos");

        RuleActivation amountActivation = new RuleActivation();
        amountActivation.setRuleId("AMOUNT");
        amountActivation.setPuntos(new BigDecimal("30"));
        amountActivation.setDescripcion("Monto 84x el promedio historico");

        lenient().when(velocityRule.evaluate(any(), any())).thenReturn(velocityActivation);
        lenient().when(amountRule.evaluate(any(), any())).thenReturn(amountActivation);

        ScoredTransaction result = scoringService.evaluarTransaccion(transaction);

        assertNotNull(result);
        assertEquals(new BigDecimal("65"), result.getScore());
        assertTrue(result.isMarcada(), "Score 65 should exceed threshold 60");
        verify(caseRepository).save(any());
    }

    @Test
    void shouldNotCreateCaseWhenBelowThreshold() {
        RuleActivation velocityActivation = new RuleActivation();
        velocityActivation.setRuleId("VELOCITY");
        velocityActivation.setPuntos(new BigDecimal("35"));
        velocityActivation.setDescripcion("3 transacciones en 5 minutos");

        lenient().when(velocityRule.evaluate(any(), any())).thenReturn(velocityActivation);
        lenient().when(amountRule.evaluate(any(), any())).thenReturn(null);

        ScoredTransaction result = scoringService.evaluarTransaccion(transaction);

        assertNotNull(result);
        assertFalse(result.isMarcada());
        assertEquals(new BigDecimal("35"), result.getScore());
        verify(caseRepository, never()).save(any());
    }
}
