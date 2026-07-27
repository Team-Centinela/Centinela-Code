package com.centinela.ingestion.infrastructure.persistence;

import com.centinela.ingestion.domain.model.*;

import java.time.Instant;

public final class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionEntity toEntity(Transaction domain) {
        return new TransactionEntity(
                domain.id().value(),
                domain.accountId().value(),
                domain.amount().amount(),
                domain.amount().currencyCode(),
                domain.merchantId() != null ? domain.merchantId().value() : null,
                domain.geoLocation() != null ? domain.geoLocation().latitude() : null,
                domain.geoLocation() != null ? domain.geoLocation().longitude() : null,
                domain.timestamp(),
                Instant.now()
        );
    }

    public static Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                new TransactionId(entity.getId()),
                new AccountId(entity.getAccountId()),
                new Money(entity.getAmount(), entity.getCurrency()),
                entity.getMerchantId() != null ? new MerchantId(entity.getMerchantId()) : null,
                entity.getLatitude() != null && entity.getLongitude() != null
                        ? new GeoLocation(entity.getLatitude(), entity.getLongitude())
                        : null,
                entity.getTimestamp()
        );
    }
}
