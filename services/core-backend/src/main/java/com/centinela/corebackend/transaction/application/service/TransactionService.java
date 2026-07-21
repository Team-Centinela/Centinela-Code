package com.centinela.corebackend.transaction.application.service;

import com.centinela.corebackend.transaction.application.dto.TransactionRequest;
import com.centinela.corebackend.transaction.application.dto.TransactionResponse;
import com.centinela.corebackend.transaction.domain.event.TransactionReceivedEvent;
import com.centinela.corebackend.transaction.domain.model.*;
import com.centinela.corebackend.transaction.domain.port.OutboxRepository;
import com.centinela.corebackend.transaction.domain.port.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public TransactionResponse create(TransactionRequest request) {
        Currency currency;
        try {
            currency = Currency.getInstance(request.getCurrency());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid currency code: " + request.getCurrency());
        }
        TransactionType type;
        try {
            type = TransactionType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + request.getType()
                    + ". Allowed values: " + java.util.Arrays.toString(TransactionType.values()));
        }
        Transaction transaction = new Transaction(
                TransactionId.generate(),
                request.getAccountId(),
                new Money(request.getAmount(), currency),
                Instant.now(),
                new GeoLocation(request.getLatitude(), request.getLongitude()),
                type,
                request.getMerchantId(),
                request.getDescription()
        );

        transactionRepository.save(transaction);
        TransactionReceivedEvent event = new TransactionReceivedEvent(
                transaction.getId(), transaction.getAccountId());
        outboxRepository.append(
                OutboxRepository.Status.PENDING,
                "Transaction",
                transaction.getId().value().toString(),
                toJson(event),
                TransactionReceivedEvent.class.getSimpleName());

        return TransactionResponse.fromDomain(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionResponse> findById(String id) {
        return transactionRepository.findById(TransactionId.fromString(id))
                .map(TransactionResponse::fromDomain);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByAccountId(String accountId) {
        return transactionRepository.findByAccountId(accountId)
                .stream()
                .map(TransactionResponse::fromDomain)
                .toList();
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox event", exception);
        }
    }
}
