package com.centinela.corebackend.transaction.infrastructure.persistence;

import com.centinela.corebackend.transaction.domain.model.*;

import java.util.Currency;

public class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionEntity toEntity(Transaction domain) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(domain.getId().value());
        entity.setAccountId(domain.getAccountId());
        entity.setAmount(domain.getAmount().amount());
        entity.setCurrency(domain.getAmount().currency().getCurrencyCode());
        entity.setTimestamp(domain.getTimestamp());
        entity.setLatitude(domain.getLocation().latitude());
        entity.setLongitude(domain.getLocation().longitude());
        entity.setType(TransactionEntity.TransactionType.valueOf(domain.getType().name()));
        entity.setMerchantId(domain.getMerchantId());
        entity.setDescription(domain.getDescription());
        entity.setStatus(TransactionEntity.TransactionStatus.valueOf(domain.getStatus().name()));
        entity.setVersion(domain.getVersion());
        return entity;
    }

    public static Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                TransactionId.fromString(entity.getId().toString()),
                entity.getAccountId(),
                new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency())),
                entity.getTimestamp(),
                new GeoLocation(entity.getLatitude(), entity.getLongitude()),
                TransactionType.valueOf(entity.getType().name()),
                entity.getMerchantId(),
                entity.getDescription(),
                entity.getVersion()
        );
    }
}
