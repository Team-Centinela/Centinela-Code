package com.centinela.corebackend.account.application.service;

import com.centinela.corebackend.account.application.dto.AccountResponse;
import com.centinela.corebackend.account.application.dto.CreateAccountRequest;
import com.centinela.corebackend.account.application.dto.TransferRequest;
import com.centinela.corebackend.account.application.dto.TransferResponse;
import com.centinela.corebackend.account.domain.model.*;
import com.centinela.corebackend.account.domain.port.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse create(CreateAccountRequest request) {
        Account account = new Account(
                new AccountId(request.getAccountId()),
                request.getOwner(),
                Currency.getInstance(request.getCurrency()),
                request.getInitialBalance()
        );
        accountRepository.save(account);
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

        Transfer transfer = new Transfer(fromId, toId, request.getAmount(), request.getDescription());
        return TransferResponse.fromDomain(transfer);
    }
}
