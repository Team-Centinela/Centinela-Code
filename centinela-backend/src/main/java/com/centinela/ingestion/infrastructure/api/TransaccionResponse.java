package com.centinela.ingestion.infrastructure.api;

import java.time.Instant;

public class TransaccionResponse {
    private String transactionId;
    private String cuentaId;
    private String status;
    private String message;
    private Instant timestamp;

    public TransaccionResponse(String transactionId, String cuentaId, String status, String message) {
        this.transactionId = transactionId;
        this.cuentaId = cuentaId;
        this.status = status;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public String getTransactionId() { return transactionId; }
    public String getCuentaId() { return cuentaId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
