package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private final TransactionId id = new TransactionId(UUID.randomUUID());
    private final AccountId accountId = new AccountId("acc-123");
    private final Money amount = new Money(new BigDecimal("150.00"), "USD");
    private final Instant now = Instant.now();

    @Test
    void should_reject_null_id() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(null, accountId, amount, null, null, now));
    }

    @Test
    void should_reject_null_accountId() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(id, null, amount, null, null, now));
    }

    @Test
    void should_reject_null_amount() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(id, accountId, null, null, null, now));
    }

    @Test
    void should_reject_null_timestamp() {
        assertThrows(NullPointerException.class,
                () -> new Transaction(id, accountId, amount, null, null, null));
    }

    @Test
    void should_accept_null_merchant() {
        Transaction tx = new Transaction(id, accountId, amount, null, null, now);
        assertNull(tx.merchantId());
    }

    @Test
    void should_accept_null_location() {
        Transaction tx = new Transaction(id, accountId, amount, null, null, now);
        assertNull(tx.geoLocation());
    }

    @Test
    void should_accept_valid_transaction() {
        MerchantId merchant = new MerchantId("merchant-1");
        GeoLocation loc = new GeoLocation(40.4168, -3.7038);
        Transaction tx = new Transaction(id, accountId, amount, merchant, loc, now);

        assertEquals(id, tx.id());
        assertEquals(accountId, tx.accountId());
        assertEquals(amount, tx.amount());
        assertEquals(merchant, tx.merchantId());
        assertEquals(loc, tx.geoLocation());
        assertEquals(now, tx.timestamp());
    }

    @Test
    void should_be_equal_by_id() {
        Transaction tx1 = new Transaction(id, accountId, amount, null, null, now);
        Transaction tx2 = new Transaction(id, accountId, amount, null, null, now);
        assertEquals(tx1, tx2);
        assertEquals(tx1.hashCode(), tx2.hashCode());
    }

    @Test
    void should_not_be_equal_to_different_id() {
        Transaction tx1 = new Transaction(id, accountId, amount, null, null, now);
        Transaction tx2 = new Transaction(new TransactionId(), accountId, amount, null, null, now);
        assertNotEquals(tx1, tx2);
    }
}
