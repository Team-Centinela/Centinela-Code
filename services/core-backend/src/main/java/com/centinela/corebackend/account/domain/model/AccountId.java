package com.centinela.corebackend.account.domain.model;

public record AccountId(String value) {

    public AccountId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AccountId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
