package com.centinela.ingestion.infrastructure.web;

import com.centinela.ingestion.application.dto.TransactionRequest;
import com.centinela.ingestion.application.service.SubmitTransactionHandler;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final SubmitTransactionHandler handler;

    public TransactionController(SubmitTransactionHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/transactions")
    public ResponseEntity<Void> submitTransaction(@Valid @RequestBody TransactionRequest request) {
        var result = handler.handle(request);
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/transactions/" + result.transactionId() + "/status"))
                .build();
    }
}
