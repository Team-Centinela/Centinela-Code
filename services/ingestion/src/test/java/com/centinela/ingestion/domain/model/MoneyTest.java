package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void should_reject_null_amount() {
        assertThrows(NullPointerException.class, () -> new Money(null, "USD"));
    }

    @Test
    void should_reject_null_currency() {
        assertThrows(NullPointerException.class,
                () -> new Money(new BigDecimal("10.00"), null));
    }

    @Test
    void should_reject_negative_amount() {
        InvalidAmountException ex = assertThrows(InvalidAmountException.class,
                () -> new Money(new BigDecimal("-5.00"), "USD"));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void should_reject_zero_amount() {
        assertThrows(InvalidAmountException.class,
                () -> new Money(BigDecimal.ZERO, "USD"));
    }

    @Test
    void should_reject_invalid_currency() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("10.00"), "INVALID"));
    }

    @Test
    void should_accept_valid_amount() {
        Money money = new Money(new BigDecimal("10.00"), "USD");
        assertEquals(0, new BigDecimal("10.00").compareTo(money.amount()));
        assertEquals("USD", money.currencyCode());
    }

    @Test
    void should_be_equal_by_value() {
        Money m1 = new Money(new BigDecimal("10.00"), "USD");
        Money m2 = new Money(new BigDecimal("10.00"), "USD");
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void should_not_be_equal_to_different_currency() {
        Money m1 = new Money(new BigDecimal("10.00"), "USD");
        Money m2 = new Money(new BigDecimal("10.00"), "EUR");
        assertNotEquals(m1, m2);
    }

    @Test
    void should_not_be_equal_to_different_amount() {
        Money m1 = new Money(new BigDecimal("10.00"), "USD");
        Money m2 = new Money(new BigDecimal("20.00"), "USD");
        assertNotEquals(m1, m2);
    }
}
