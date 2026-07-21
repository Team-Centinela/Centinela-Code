package com.centinela.corebackend.transaction.application.service;

import com.centinela.corebackend.transaction.application.dto.TransactionRequest;
import com.centinela.corebackend.transaction.domain.port.OutboxRepository;
import com.centinela.corebackend.transaction.domain.port.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Test
    void create_persists_transaction_received_event_in_outbox() {
        TransactionService service = new TransactionService(
                transactionRepository, outboxRepository, new ObjectMapper().findAndRegisterModules());
        TransactionRequest request = new TransactionRequest();
        request.setAccountId("acc-001");
        request.setAmount(new BigDecimal("25.00"));
        request.setCurrency("USD");
        request.setLatitude(4.6097);
        request.setLongitude(-74.0817);
        request.setType("PURCHASE");

        service.create(request);

        verify(transactionRepository).save(any());
        verify(outboxRepository).append(
                eq(OutboxRepository.Status.PENDING), eq("Transaction"), any(),
                contains("acc-001"), eq("TransactionReceivedEvent"));
    }
}
