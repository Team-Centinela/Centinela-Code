package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MerchantIdTest {

    @Test
    void should_reject_null() {
        assertThrows(IllegalArgumentException.class, () -> new MerchantId(null));
    }

    @Test
    void should_reject_blank() {
        assertThrows(IllegalArgumentException.class, () -> new MerchantId(" "));
    }

    @Test
    void should_accept_valid_value() {
        MerchantId id = new MerchantId("merchant-1");
        assertEquals("merchant-1", id.value());
    }

    @Test
    void should_be_equal_by_value() {
        MerchantId id1 = new MerchantId("merchant-1");
        MerchantId id2 = new MerchantId("merchant-1");
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
