package com.centinela.ingestion.domain.model;

import java.util.Objects;

public final class MerchantId {
    private final String value;

    public MerchantId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MerchantId must not be null or blank");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
