package com.centinela.corebackend.account.application.service;

import com.centinela.corebackend.account.application.dto.AccountResponse;
import com.centinela.corebackend.account.application.dto.CreateAccountRequest;
import com.centinela.corebackend.account.application.dto.TransferRequest;
import com.centinela.corebackend.account.application.dto.TransferResponse;
import com.centinela.corebackend.account.domain.model.Account;
import com.centinela.corebackend.account.domain.model.AccountId;
import com.centinela.corebackend.account.domain.model.InsufficientBalanceException;
import com.centinela.corebackend.account.domain.port.AccountRepository;
import com.centinela.corebackend.account.infrastructure.persistence.TransferJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferJpaRepository transferJpaRepository;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transferJpaRepository);
    }

    @Test
    void create_saves_and_returns_account() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountId("acc-001");
        request.setOwner("Test Owner");
        request.setCurrency("usd");
        request.setInitialBalance(new BigDecimal("100.00"));

        AccountResponse response = accountService.create(request);

        assertEquals("acc-001", response.getAccountId());
        assertEquals("Test Owner", response.getOwner());
        assertEquals("USD", response.getCurrency());
        assertEquals(0, new BigDecimal("100.00").compareTo(response.getBalance()));

        verify(accountRepository).save(accountCaptor.capture());
        Account saved = accountCaptor.getValue();
        assertEquals("acc-001", saved.getId().value());
    }

    @Test
    void findById_returns_account_when_found() {
        Account account = new Account(
                new AccountId("acc-001"),
                "Owner",
                java.util.Currency.getInstance("USD"),
                new BigDecimal("50.00")
        );
        when(accountRepository.findById(new AccountId("acc-001")))
                .thenReturn(Optional.of(account));

        AccountResponse response = accountService.findById("acc-001");

        assertEquals("acc-001", response.getAccountId());
        assertEquals("Owner", response.getOwner());
    }

    @Test
    void findById_throws_when_not_found() {
        when(accountRepository.findById(new AccountId("missing")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                accountService.findById("missing"));
    }

    @Test
    void transfer_succeeds_with_sufficient_balance() {
        Account from = new Account(
                new AccountId("acc-001"), "From", java.util.Currency.getInstance("USD"), new BigDecimal("200.00"));
        Account to = new Account(
                new AccountId("acc-002"), "To", java.util.Currency.getInstance("USD"), BigDecimal.ZERO);

        when(accountRepository.findById(new AccountId("acc-001"))).thenReturn(Optional.of(from));
        when(accountRepository.findById(new AccountId("acc-002"))).thenReturn(Optional.of(to));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId("acc-001");
        request.setToAccountId("acc-002");
        request.setAmount(new BigDecimal("150.00"));
        request.setDescription("test transfer");

        TransferResponse response = accountService.transfer(request);

        assertEquals("acc-001", response.getFromAccountId());
        assertEquals("acc-002", response.getToAccountId());
        assertEquals(0, new BigDecimal("150.00").compareTo(response.getAmount()));

        verify(accountRepository, times(2)).save(any());
        verify(transferJpaRepository).save(any());
    }

    @Test
    void transfer_throws_when_source_insufficient() {
        Account from = new Account(
                new AccountId("acc-001"), "From", java.util.Currency.getInstance("USD"), new BigDecimal("10.00"));
        Account to = new Account(
                new AccountId("acc-002"), "To", java.util.Currency.getInstance("USD"), BigDecimal.ZERO);

        when(accountRepository.findById(new AccountId("acc-001"))).thenReturn(Optional.of(from));
        when(accountRepository.findById(new AccountId("acc-002"))).thenReturn(Optional.of(to));

        TransferRequest request = new TransferRequest();
        request.setFromAccountId("acc-001");
        request.setToAccountId("acc-002");
        request.setAmount(new BigDecimal("100.00"));

        assertThrows(InsufficientBalanceException.class, () ->
                accountService.transfer(request));
    }

    @Test
    void transfer_throws_when_source_not_found() {
        when(accountRepository.findById(new AccountId("missing"))).thenReturn(Optional.empty());

        TransferRequest request = new TransferRequest();
        request.setFromAccountId("missing");
        request.setToAccountId("acc-002");
        request.setAmount(new BigDecimal("50.00"));

        assertThrows(IllegalArgumentException.class, () ->
                accountService.transfer(request));
    }

    @Test
    void create_uppercases_currency_code() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountId("acc-003");
        request.setOwner("Lowercase Currency");
        request.setCurrency("eur");
        request.setInitialBalance(BigDecimal.ZERO);

        AccountResponse response = accountService.create(request);

        assertEquals("EUR", response.getCurrency());
    }
}
