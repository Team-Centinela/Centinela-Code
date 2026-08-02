package com.centinela.ingestion.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "oltp")
public class TransactionEntity {

    /**
     * No @Version (optimistic locking) because transactions are append-only:
     * once inserted, a row is never updated. Concurrent inserts are handled by
     * the database (hash partitioning + unique id), so optimistic locking adds
     * no safety guarantee — only runtime overhead.
     */
    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "merchant_id")
    private String merchantId;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TransactionEntity() {}

    public TransactionEntity(UUID id, String accountId, BigDecimal amount, String currency,
                             String merchantId, Double latitude, Double longitude,
                             Instant timestamp, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
