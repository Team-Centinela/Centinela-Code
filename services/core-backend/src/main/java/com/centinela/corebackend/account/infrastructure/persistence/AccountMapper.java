package com.centinela.corebackend.account.infrastructure.persistence;

import com.centinela.corebackend.account.domain.model.Account;
import com.centinela.corebackend.account.domain.model.AccountId;

import java.util.Currency;

public class AccountMapper {

    private AccountMapper() {}

    public static AccountEntity toEntity(Account domain) {
        AccountEntity entity = new AccountEntity();
        entity.setId(domain.getId().value());
        entity.setOwner(domain.getOwner());
        entity.setCurrency(domain.getCurrency().getCurrencyCode());
        entity.setBalance(domain.getBalance());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    public static Account toDomain(AccountEntity entity) {
        return new Account(
                new AccountId(entity.getId()),
                entity.getOwner(),
                Currency.getInstance(entity.getCurrency()),
                entity.getBalance(),
                entity.getCreatedAt()
        );
    }
}
