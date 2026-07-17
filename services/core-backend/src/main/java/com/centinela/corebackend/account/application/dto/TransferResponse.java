package com.centinela.corebackend.account.application.dto;

import com.centinela.corebackend.account.domain.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;

public class TransferResponse {

    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String description;
    private Instant timestamp;

    public static TransferResponse fromDomain(Transfer transfer) {
        TransferResponse r = new TransferResponse();
        r.fromAccountId = transfer.getFromAccountId().value();
        r.toAccountId = transfer.getToAccountId().value();
        r.amount = transfer.getAmount();
        r.description = transfer.getDescription();
        r.timestamp = transfer.getTimestamp();
        return r;
    }

    public String getFromAccountId() { return fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}
