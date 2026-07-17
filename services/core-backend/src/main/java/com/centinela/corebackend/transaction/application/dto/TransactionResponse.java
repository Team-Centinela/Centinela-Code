package com.centinela.corebackend.transaction.application.dto;

import com.centinela.corebackend.transaction.domain.model.*;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private String id;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private double latitude;
    private double longitude;
    private String type;
    private String merchantId;
    private String description;
    private String status;

    public static TransactionResponse fromDomain(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.id = t.getId().toString();
        r.accountId = t.getAccountId();
        r.amount = t.getAmount().amount();
        r.currency = t.getAmount().currency().getCurrencyCode();
        r.timestamp = t.getTimestamp();
        r.latitude = t.getLocation().latitude();
        r.longitude = t.getLocation().longitude();
        r.type = t.getType().name();
        r.merchantId = t.getMerchantId();
        r.description = t.getDescription();
        r.status = t.getStatus().name();
        return r;
    }

    public String getId() { return id; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getTimestamp() { return timestamp; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getType() { return type; }
    public String getMerchantId() { return merchantId; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
}
