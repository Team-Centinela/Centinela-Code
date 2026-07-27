package com.centinela.ingestion.domain.service;

import com.centinela.ingestion.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TransactionFactoryTest {

    @Test
    void should_create_transaction_with_all_fields() {
        CreateTransactionCommand cmd = new CreateTransactionCommand(
                new AccountId("acc-123"),
                new BigDecimal("250.00"),
                "USD",
                "merchant-1",
                40.4168, -3.7038,
                Instant.parse("2026-07-22T10:00:00Z")
        );

        Transaction tx = TransactionFactory.create(cmd);

        assertNotNull(tx.id());
        assertEquals(new AccountId("acc-123"), tx.accountId());
        assertEquals(new Money(new BigDecimal("250.00"), "USD"), tx.amount());
        assertEquals(new MerchantId("merchant-1"), tx.merchantId());
        assertEquals(new GeoLocation(40.4168, -3.7038), tx.geoLocation());
        assertEquals(Instant.parse("2026-07-22T10:00:00Z"), tx.timestamp());
    }

    @Test
    void should_create_transaction_without_merchant_and_location() {
        CreateTransactionCommand cmd = new CreateTransactionCommand(
                new AccountId("acc-123"),
                new BigDecimal("100.00"),
                "USD",
                null, null, null,
                Instant.now()
        );

        Transaction tx = TransactionFactory.create(cmd);

        assertNull(tx.merchantId());
        assertNull(tx.geoLocation());
    }

    @Test
    void should_use_current_timestamp_when_null() {
        CreateTransactionCommand cmd = new CreateTransactionCommand(
                new AccountId("acc-123"),
                new BigDecimal("100.00"),
                "USD",
                null, null, null,
                null
        );

        Transaction tx = TransactionFactory.create(cmd);
        assertNotNull(tx.timestamp());
    }

    @Test
    void should_reject_negative_amount() {
        CreateTransactionCommand cmd = new CreateTransactionCommand(
                new AccountId("acc-123"),
                new BigDecimal("-50.00"),
                "USD",
                null, null, null,
                Instant.now()
        );

        assertThrows(InvalidAmountException.class, () -> TransactionFactory.create(cmd));
    }
}
