package com.centinela.corebackend.account.domain.port;

import com.centinela.corebackend.account.domain.model.Account;
import com.centinela.corebackend.account.domain.model.AccountId;

import java.util.Optional;

public interface AccountRepository {

    void save(Account account);

    Optional<Account> findById(AccountId id);
}
