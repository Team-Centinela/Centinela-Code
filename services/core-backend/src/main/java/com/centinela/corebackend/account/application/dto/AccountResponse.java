package com.centinela.corebackend.account.application.dto;

import com.centinela.corebackend.account.domain.model.Account;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountResponse {

    private String accountId;
    private String owner;
    private String currency;
    private BigDecimal balance;
    private Instant createdAt;

    public static AccountResponse fromDomain(Account account) {
        AccountResponse r = new AccountResponse();
        r.accountId = account.getId().value();
        r.owner = account.getOwner();
        r.currency = account.getCurrency().getCurrencyCode();
        r.balance = account.getBalance();
        r.createdAt = account.getCreatedAt();
        return r;
    }

    public String getAccountId() { return accountId; }
    public String getOwner() { return owner; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}
