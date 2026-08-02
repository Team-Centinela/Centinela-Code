package com.centinela.ingestion.domain.event;

import com.centinela.ingestion.domain.model.Money;
import com.centinela.ingestion.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionReceivedTest {

    @Test
    void should_create_with_valid_args() {
        TransactionId txId = new TransactionId();
        Money amount = new Money(new BigDecimal("150.00"), "USD");
        UUID correlationId = UUID.randomUUID();

        TransactionReceived event = new TransactionReceived(txId, amount, correlationId);

        assertNotNull(event.eventId());
        assertEquals("TransactionReceived", event.eventType());
        assertEquals("IngestionApi", event.source());
        assertNotNull(event.timestamp());
        assertEquals(correlationId, event.correlationId());
        assertEquals(txId.value(), event.data().transactionId());
        assertEquals(new BigDecimal("150.00"), event.data().amount());
        assertEquals("USD", event.data().currency());
    }

    @Test
    void should_reject_null_eventId() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceived(
                null, "type", "source", Instant.now(), UUID.randomUUID(),
                new TransactionReceived.Data(UUID.randomUUID(), BigDecimal.TEN, "USD")
        ));
    }

    @Test
    void should_reject_null_data() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceived(
                UUID.randomUUID(), "type", "source", Instant.now(),
                UUID.randomUUID(), null
        ));
    }
}
