package com.centinela.corebackend.account.infrastructure.persistence;

import com.centinela.corebackend.account.domain.model.Account;
import com.centinela.corebackend.account.domain.model.AccountId;
import com.centinela.corebackend.account.domain.port.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaAccountRepository implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public JpaAccountRepository(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Account account) {
        jpaRepository.save(AccountMapper.toEntity(account));
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpaRepository.findById(id.value()).map(AccountMapper::toDomain);
    }
}
