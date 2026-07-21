package com.centinela.corebackend.account.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountCreatedEvent(String accountId, String owner, String currency,
                                  BigDecimal initialBalance, Instant occurredAt) {
}
