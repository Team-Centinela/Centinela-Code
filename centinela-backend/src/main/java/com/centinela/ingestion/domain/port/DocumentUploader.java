package com.centinela.ingestion.domain.port;

import com.centinela.ingestion.domain.model.Transaccion;

public interface DocumentUploader {
    String upload(String transactionId, String fileName, byte[] content, String contentType);
}
