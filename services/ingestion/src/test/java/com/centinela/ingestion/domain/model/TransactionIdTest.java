package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionIdTest {

    @Test
    void should_reject_null_uuid() {
        assertThrows(NullPointerException.class, () -> new TransactionId(null));
    }

    @Test
    void should_create_with_random_uuid_when_no_arg() {
        TransactionId id = new TransactionId();
        assertNotNull(id.value());
    }

    @Test
    void should_be_equal_by_value() {
        UUID uuid = UUID.randomUUID();
        TransactionId id1 = new TransactionId(uuid);
        TransactionId id2 = new TransactionId(uuid);
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void should_not_be_equal_to_different_id() {
        TransactionId id1 = new TransactionId();
        TransactionId id2 = new TransactionId();
        assertNotEquals(id1, id2);
    }

    @Test
    void should_parse_from_string() {
        UUID uuid = UUID.randomUUID();
        TransactionId id = TransactionId.fromString(uuid.toString());
        assertEquals(new TransactionId(uuid), id);
    }

    @Test
    void should_throw_on_invalid_string() {
        assertThrows(IllegalArgumentException.class, () -> TransactionId.fromString("not-a-uuid"));
    }
}
