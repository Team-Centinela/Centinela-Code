package com.centinela.corebackend.account.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferCompletedEvent(String transferId, String fromAccountId, String toAccountId,
                                     BigDecimal amount, String description, Instant occurredAt) {
}
