package com.centinela.ingestion.domain.service;

import com.centinela.ingestion.domain.model.*;

import java.time.Instant;
import java.util.UUID;

public final class TransactionFactory {
    private TransactionFactory() {
    }

    public static Transaction create(CreateTransactionCommand cmd) {
        TransactionId id = new TransactionId(UUID.randomUUID());
        Money money = new Money(cmd.amount(), cmd.currency());
        MerchantId merchant = cmd.merchantId() != null
                ? new MerchantId(cmd.merchantId())
                : null;
        GeoLocation geo = null;
        if (cmd.latitude() != null && cmd.longitude() != null) {
            geo = new GeoLocation(cmd.latitude(), cmd.longitude());
        }
        Instant ts = cmd.timestamp() != null ? cmd.timestamp() : Instant.now();
        return new Transaction(id, cmd.accountId(), money, merchant, geo, ts);
    }
}
