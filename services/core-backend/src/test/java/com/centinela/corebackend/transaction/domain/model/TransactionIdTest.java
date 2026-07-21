package com.centinela.corebackend.transaction.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionIdTest {

    @Test
    void rejects_null_uuid() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionId(null));
    }

    @Test
    void generate_creates_non_null_id() {
        TransactionId id = TransactionId.generate();
        assertNotNull(id);
        assertNotNull(id.value());
    }

    @Test
    void generate_creates_unique_ids() {
        TransactionId id1 = TransactionId.generate();
        TransactionId id2 = TransactionId.generate();
        assertNotEquals(id1, id2);
    }

    @Test
    void fromString_parses_valid_uuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        TransactionId id = TransactionId.fromString(uuid);
        assertEquals(UUID.fromString(uuid), id.value());
    }

    @Test
    void fromString_throws_on_invalid_uuid() {
        assertThrows(IllegalArgumentException.class, () ->
                TransactionId.fromString("not-a-uuid"));
    }

    @Test
    void toString_returns_uuid_string() {
        UUID uuid = UUID.randomUUID();
        TransactionId id = new TransactionId(uuid);
        assertEquals(uuid.toString(), id.toString());
    }
}
