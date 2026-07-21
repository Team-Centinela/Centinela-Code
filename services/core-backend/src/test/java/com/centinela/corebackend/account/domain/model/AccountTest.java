package com.centinela.corebackend.account.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private Account createAccount(BigDecimal initialBalance) {
        return new Account(
                new AccountId("acc-001"),
                "Test Owner",
                Currency.getInstance("USD"),
                initialBalance
        );
    }

    @Test
    void debit_subtracts_exact_amount() {
        Account account = createAccount(new BigDecimal("100.00"));
        account.debit(new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("60.00").compareTo(account.getBalance()));
    }

    @Test
    void debit_throws_when_balance_below_amount() {
        Account account = createAccount(new BigDecimal("30.00"));
        assertThrows(InsufficientBalanceException.class, () ->
                account.debit(new BigDecimal("50.00")));
    }

    @Test
    void debit_rejects_non_positive_amount() {
        Account account = createAccount(new BigDecimal("100.00"));
        assertThrows(IllegalArgumentException.class, () ->
                account.debit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                account.debit(new BigDecimal("-10.00")));
    }

    @Test
    void credit_adds_exact_amount() {
        Account account = createAccount(new BigDecimal("50.00"));
        account.credit(new BigDecimal("25.00"));
        assertEquals(0, new BigDecimal("75.00").compareTo(account.getBalance()));
    }

    @Test
    void credit_rejects_non_positive_amount() {
        Account account = createAccount(new BigDecimal("100.00"));
        assertThrows(IllegalArgumentException.class, () ->
                account.credit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                account.credit(new BigDecimal("-5.00")));
    }

    @Test
    void debit_exact_balance_empties_account() {
        Account account = createAccount(new BigDecimal("50.00"));
        account.debit(new BigDecimal("50.00"));
        assertEquals(0, BigDecimal.ZERO.compareTo(account.getBalance()));
    }

    @Test
    void multiple_credits_and_debits_maintain_correct_balance() {
        Account account = createAccount(new BigDecimal("100.00"));
        account.credit(new BigDecimal("50.00"));
        account.debit(new BigDecimal("30.00"));
        account.credit(new BigDecimal("20.00"));
        assertEquals(0, new BigDecimal("140.00").compareTo(account.getBalance()));
    }
}
