package com.centinela.corebackend.account.domain.model;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    private final AccountId accountId;
    private final BigDecimal balance;
    private final BigDecimal attempted;

    public InsufficientBalanceException(AccountId accountId, BigDecimal balance, BigDecimal attempted) {
        super("Account %s has balance %s but attempted debit of %s"
                .formatted(accountId, balance, attempted));
        this.accountId = accountId;
        this.balance = balance;
        this.attempted = attempted;
    }

    public AccountId getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getAttempted() { return attempted; }
}
