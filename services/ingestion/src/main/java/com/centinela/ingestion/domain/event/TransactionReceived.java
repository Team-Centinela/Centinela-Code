package com.centinela.ingestion.domain.event;

import com.centinela.ingestion.domain.model.Money;
import com.centinela.ingestion.domain.model.TransactionId;

import java.time.Instant;
import java.util.UUID;

public record TransactionReceived(
        UUID eventId,
        String eventType,
        String source,
        Instant timestamp,
        UUID correlationId,
        TransactionReceived.Data data
) {
    public static final String EVENT_TYPE = "TransactionReceived";
    public static final String SOURCE = "IngestionApi";

    public TransactionReceived {
        if (eventId == null) throw new IllegalArgumentException("eventId must not be null");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (timestamp == null) throw new IllegalArgumentException("timestamp must not be null");
        if (correlationId == null) throw new IllegalArgumentException("correlationId must not be null");
        if (data == null) throw new IllegalArgumentException("data must not be null");
    }

    public TransactionReceived(TransactionId transactionId, Money amount, UUID correlationId) {
        this(
                UUID.randomUUID(),
                EVENT_TYPE,
                SOURCE,
                Instant.now(),
                correlationId,
                new Data(transactionId.value(), amount.amount(), amount.currencyCode())
        );
    }

    public record Data(
            UUID transactionId,
            java.math.BigDecimal amount,
            String currency
    ) {
        public Data {
            if (transactionId == null) throw new IllegalArgumentException("transactionId must not be null");
            if (amount == null) throw new IllegalArgumentException("amount must not be null");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        }
    }
}
