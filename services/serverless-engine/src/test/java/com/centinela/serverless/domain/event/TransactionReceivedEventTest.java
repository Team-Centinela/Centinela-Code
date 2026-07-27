package com.centinela.serverless.domain.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionReceivedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        UUID txId = UUID.randomUUID();
        String json = """
                {
                    "transactionId": "%s",
                    "accountId": "acc-123",
                    "amount": 150.00,
                    "currency": "USD",
                    "merchantId": "merchant-xyz",
                    "latitude": 40.7128,
                    "longitude": -74.0060,
                    "timestamp": "2026-07-24T12:00:00Z"
                }
                """.formatted(txId.toString());

        TransactionReceivedEvent event = objectMapper.readValue(json, TransactionReceivedEvent.class);

        assertEquals(txId, event.transactionId());
        assertEquals("acc-123", event.accountId());
        assertEquals(new BigDecimal("150.00"), event.amount());
        assertEquals("USD", event.currency());
        assertEquals("merchant-xyz", event.merchantId());
        assertEquals(40.7128, event.latitude(), 0.0001);
        assertEquals(-74.0060, event.longitude(), 0.0001);
        assertEquals(Instant.parse("2026-07-24T12:00:00Z"), event.timestamp());
    }

    @Test
    void shouldRejectNullTransactionId() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceivedEvent(
                null, "acc-1", BigDecimal.TEN, "USD", null, null, null, Instant.now()
        ));
    }

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceivedEvent(
                UUID.randomUUID(), "   ", BigDecimal.TEN, "USD", null, null, null, Instant.now()
        ));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-1", new BigDecimal("-10"), "USD", null, null, null, Instant.now()
        ));
    }

    @Test
    void shouldRejectBlankCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-1", BigDecimal.TEN, "", null, null, null, Instant.now()
        ));
    }

    @Test
    void shouldRejectNullTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReceivedEvent(
                UUID.randomUUID(), "acc-1", BigDecimal.TEN, "USD", null, null, null, null
        ));
    }
}
