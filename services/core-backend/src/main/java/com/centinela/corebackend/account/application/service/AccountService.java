package com.centinela.corebackend.account.application.service;

import com.centinela.corebackend.account.application.dto.AccountResponse;
import com.centinela.corebackend.account.application.dto.CreateAccountRequest;
import com.centinela.corebackend.account.application.dto.TransferRequest;
import com.centinela.corebackend.account.application.dto.TransferResponse;
import com.centinela.corebackend.account.domain.event.AccountCreatedEvent;
import com.centinela.corebackend.account.domain.event.TransferCompletedEvent;
import com.centinela.corebackend.account.domain.model.*;
import com.centinela.corebackend.account.domain.port.AccountRepository;
import com.centinela.corebackend.account.domain.port.OutboxRepository;
import com.centinela.corebackend.account.infrastructure.persistence.TransferEntity;
import com.centinela.corebackend.account.infrastructure.persistence.TransferJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransferJpaRepository transferJpaRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository, TransferJpaRepository transferJpaRepository,
                          OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transferJpaRepository = transferJpaRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public AccountResponse create(CreateAccountRequest request) {
        Account account = new Account(
                new AccountId(request.getAccountId()),
                request.getOwner(),
                Currency.getInstance(request.getCurrency().toUpperCase(Locale.ROOT)),
                request.getInitialBalance()
        );
        accountRepository.save(account);
        AccountCreatedEvent event = new AccountCreatedEvent(
                account.getId().value(), account.getOwner(), account.getCurrency().getCurrencyCode(),
                account.getBalance(), account.getCreatedAt());
        outboxRepository.append(
                OutboxRepository.Status.PENDING,
                "Account",
                account.getId().value(),
                toJson(event),
                AccountCreatedEvent.class.getSimpleName());
        return AccountResponse.fromDomain(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(String accountId) {
        return accountRepository.findById(new AccountId(accountId))
                .map(AccountResponse::fromDomain)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    public TransferResponse transfer(TransferRequest request) {
        AccountId fromId = new AccountId(request.getFromAccountId());
        AccountId toId = new AccountId(request.getToAccountId());

        Account from = accountRepository.findById(fromId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + fromId));
        Account to = accountRepository.findById(toId)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + toId));

        from.debit(request.getAmount());
        to.credit(request.getAmount());

        accountRepository.save(from);
        accountRepository.save(to);

        TransferEntity transferEntity = new TransferEntity();
        transferEntity.setId(UUID.randomUUID());
        transferEntity.setFromAccountId(fromId.value());
        transferEntity.setToAccountId(toId.value());
        transferEntity.setAmount(request.getAmount());
        transferEntity.setDescription(request.getDescription());
        transferEntity.setTimestamp(java.time.Instant.now());
        transferJpaRepository.save(transferEntity);

        Transfer transfer = new Transfer(fromId, toId, request.getAmount(), request.getDescription());
        TransferCompletedEvent event = new TransferCompletedEvent(
                transferEntity.getId().toString(), fromId.value(), toId.value(), request.getAmount(),
                request.getDescription(), transferEntity.getTimestamp());
        outboxRepository.append(
                OutboxRepository.Status.PENDING,
                "Transfer",
                transferEntity.getId().toString(),
                toJson(event),
                TransferCompletedEvent.class.getSimpleName());
        return TransferResponse.fromDomain(transfer);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox event", exception);
        }
    }
}
