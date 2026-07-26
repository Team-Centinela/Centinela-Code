package com.centinela.ingestion.domain.model;

import java.util.Objects;
import java.util.UUID;

public final class TransactionId {
    private final UUID value;

    public TransactionId() {
        this(UUID.randomUUID());
    }

    public TransactionId(UUID value) {
        this.value = Objects.requireNonNull(value, "TransactionId must not be null");
    }

    public UUID value() {
        return value;
    }

    public static TransactionId fromString(String s) {
        return new TransactionId(UUID.fromString(s));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
