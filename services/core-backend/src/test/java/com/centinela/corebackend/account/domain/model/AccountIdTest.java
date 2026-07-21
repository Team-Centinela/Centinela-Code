package com.centinela.corebackend.account.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountIdTest {

    @Test
    void rejects_null_value() {
        assertThrows(IllegalArgumentException.class, () -> new AccountId(null));
    }

    @Test
    void rejects_blank_value() {
        assertThrows(IllegalArgumentException.class, () -> new AccountId("  "));
    }

    @Test
    void accepts_valid_value() {
        AccountId id = new AccountId("acc-001");
        assertEquals("acc-001", id.value());
    }

    @Test
    void toString_returns_value() {
        AccountId id = new AccountId("test-id");
        assertEquals("test-id", id.toString());
    }
}
