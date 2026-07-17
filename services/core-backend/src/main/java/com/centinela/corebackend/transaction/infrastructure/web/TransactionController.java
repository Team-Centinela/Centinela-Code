package com.centinela.corebackend.transaction.infrastructure.web;

import com.centinela.corebackend.transaction.application.dto.TransactionRequest;
import com.centinela.corebackend.transaction.application.dto.TransactionResponse;
import com.centinela.corebackend.transaction.application.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String id) {
        return transactionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<java.util.List<TransactionResponse>> getByAccount(@RequestParam String accountId) {
        return ResponseEntity.ok(transactionService.findByAccountId(accountId));
    }
}
