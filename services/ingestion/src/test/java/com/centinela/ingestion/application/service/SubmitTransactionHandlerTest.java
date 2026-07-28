package com.centinela.ingestion.application.service;

import com.centinela.ingestion.application.dto.TransactionRequest;
import com.centinela.ingestion.domain.model.Money;
import com.centinela.ingestion.domain.model.Transaction;
import com.centinela.ingestion.domain.model.TransactionId;
import com.centinela.ingestion.domain.port.OutboxEventPort;
import com.centinela.ingestion.domain.port.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubmitTransactionHandlerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventPort outboxEventPort;

    private SubmitTransactionHandler handler;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        handler = new SubmitTransactionHandler(transactionRepository, outboxEventPort, mapper);
    }

    @Test
    void should_persist_transaction_and_outbox_event_atomically() {
        UUID txId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                txId,
                "acc-123",
                new BigDecimal("150.00"),
                "USD",
                "merchant-1",
                40.4168, -3.7038,
                Instant.parse("2026-07-22T10:00:00Z"),
                "idem-key-1"
        );

        SubmitTransactionHandler.SubmitResult result = handler.handle(request);

        assertNotNull(result);
        assertEquals(txId, result.transactionId());

        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxEventPort).save(any());
    }

    @Test
    void should_return_202_with_transaction_id() {
        UUID txId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                txId,
                "acc-456",
                new BigDecimal("250.00"),
                "EUR",
                null, null, null,
                Instant.now(),
                "idem-key-2"
        );

        SubmitTransactionHandler.SubmitResult result = handler.handle(request);

        assertEquals(txId, result.transactionId());
        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxEventPort).save(any());
    }

    @Test
    void should_store_correct_amount_in_transaction() {
        UUID txId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");
        TransactionRequest request = new TransactionRequest(
                txId,
                "acc-789",
                amount,
                "USD",
                null, null, null,
                Instant.parse("2026-07-22T12:00:00Z"),
                "idem-key-3"
        );

        handler.handle(request);

        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction saved = transactionCaptor.getValue();
        assertEquals(0, amount.compareTo(saved.amount().amount()));
        assertEquals("USD", saved.amount().currencyCode());
    }
}
