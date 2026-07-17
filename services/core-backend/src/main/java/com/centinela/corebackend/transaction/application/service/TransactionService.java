package com.centinela.corebackend.transaction.application.service;

import com.centinela.corebackend.transaction.application.dto.TransactionRequest;
import com.centinela.corebackend.transaction.application.dto.TransactionResponse;
import com.centinela.corebackend.transaction.domain.event.TransactionReceivedEvent;
import com.centinela.corebackend.transaction.domain.model.*;
import com.centinela.corebackend.transaction.domain.port.TransactionRepository;
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

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionResponse create(TransactionRequest request) {
        Transaction transaction = new Transaction(
                TransactionId.generate(),
                request.getAccountId(),
                new Money(request.getAmount(), Currency.getInstance(request.getCurrency())),
                Instant.now(),
                new GeoLocation(request.getLatitude(), request.getLongitude()),
                TransactionType.valueOf(request.getType()),
                request.getMerchantId(),
                request.getDescription()
        );

        transactionRepository.save(transaction);

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
}
