package com.centinela.ingestion.application.service;

import com.centinela.ingestion.application.dto.TransactionRequest;
import com.centinela.ingestion.domain.event.TransactionReceived;
import com.centinela.ingestion.domain.model.*;
import com.centinela.ingestion.domain.port.OutboxEvent;
import com.centinela.ingestion.domain.port.OutboxEventPort;
import com.centinela.ingestion.domain.port.TransactionRepository;
import com.centinela.ingestion.domain.service.CreateTransactionCommand;
import com.centinela.ingestion.domain.service.TransactionFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SubmitTransactionHandler {

    private final TransactionRepository transactionRepository;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    public SubmitTransactionHandler(TransactionRepository transactionRepository,
                                    OutboxEventPort outboxEventPort,
                                    ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.outboxEventPort = outboxEventPort;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SubmitResult handle(TransactionRequest request) {
        AccountId accountId = new AccountId(request.getAccountId());
        CreateTransactionCommand cmd = new CreateTransactionCommand(
                accountId,
                request.getAmount(),
                request.getCurrency(),
                request.getMerchantId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getTimestamp()
        );

        TransactionId txId = new TransactionId(request.getTransactionId());
        Transaction tx = new Transaction(
                txId,
                accountId,
                new Money(request.getAmount(), request.getCurrency()),
                request.getMerchantId() != null ? new MerchantId(request.getMerchantId()) : null,
                request.getLatitude() != null && request.getLongitude() != null
                        ? new GeoLocation(request.getLatitude(), request.getLongitude())
                        : null,
                request.getTimestamp()
        );

        transactionRepository.save(tx);

        TransactionReceived event = new TransactionReceived(
                UUID.randomUUID(),
                TransactionReceived.EVENT_TYPE,
                TransactionReceived.SOURCE,
                Instant.now(),
                txId.value(),
                new TransactionReceived.Data(
                        txId.value(),
                        request.getAmount(),
                        request.getCurrency()
                )
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventPort.save(new OutboxEvent(
                    UUID.randomUUID(),
                    "Transaction",
                    txId.toString(),
                    "TransactionReceived",
                    payload,
                    Instant.now(),
                    0
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return new SubmitResult(txId.value());
    }

    public record SubmitResult(UUID transactionId) {}
}
