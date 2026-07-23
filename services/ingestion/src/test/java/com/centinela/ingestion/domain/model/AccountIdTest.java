package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountIdTest {

    @Test
    void should_reject_null() {
        assertThrows(IllegalArgumentException.class, () -> new AccountId(null));
    }

    @Test
    void should_reject_blank() {
        assertThrows(IllegalArgumentException.class, () -> new AccountId(" "));
    }

    @Test
    void should_accept_valid_value() {
        AccountId id = new AccountId("acc-123");
        assertEquals("acc-123", id.value());
    }

    @Test
    void should_be_equal_by_value() {
        AccountId id1 = new AccountId("acc-123");
        AccountId id2 = new AccountId("acc-123");
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void should_not_be_equal_to_different_value() {
        AccountId id1 = new AccountId("acc-123");
        AccountId id2 = new AccountId("acc-456");
        assertNotEquals(id1, id2);
    }
}
