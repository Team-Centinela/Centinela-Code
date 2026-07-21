package com.centinela.corebackend.transaction.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void rejects_null_amount() {
        assertThrows(IllegalArgumentException.class, () ->
                new Money(null, Currency.getInstance("USD")));
    }

    @Test
    void rejects_null_currency() {
        assertThrows(IllegalArgumentException.class, () ->
                new Money(BigDecimal.TEN, null));
    }

    @Test
    void rejects_negative_amount() {
        assertThrows(IllegalArgumentException.class, () ->
                new Money(new BigDecimal("-1.00"), Currency.getInstance("USD")));
    }

    @Test
    void accepts_zero_amount() {
        Money zero = new Money(BigDecimal.ZERO, Currency.getInstance("USD"));
        assertEquals(0, BigDecimal.ZERO.compareTo(zero.amount()));
    }

    @Test
    void accepts_positive_amount() {
        Money money = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money.amount()));
    }

    @Test
    void usd_factory_creates_usd_money() {
        Money usd = Money.usd(new BigDecimal("50.00"));
        assertEquals("USD", usd.currency().getCurrencyCode());
        assertEquals(0, new BigDecimal("50.00").compareTo(usd.amount()));
    }

    @Test
    void isGreaterThan_returns_true_when_larger() {
        Money large = new Money(new BigDecimal("200.00"), Currency.getInstance("USD"));
        Money small = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertTrue(large.isGreaterThan(small));
    }

    @Test
    void isGreaterThan_returns_false_when_smaller() {
        Money large = new Money(new BigDecimal("200.00"), Currency.getInstance("USD"));
        Money small = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertFalse(small.isGreaterThan(large));
    }

    @Test
    void isGreaterThan_returns_false_when_equal() {
        Money a = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
        Money b = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertFalse(a.isGreaterThan(b));
    }
}
