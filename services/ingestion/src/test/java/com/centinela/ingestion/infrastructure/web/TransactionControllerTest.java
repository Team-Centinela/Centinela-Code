package com.centinela.ingestion.infrastructure.web;

import com.centinela.ingestion.application.dto.TransactionRequest;
import com.centinela.ingestion.application.service.SubmitTransactionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitTransactionHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void should_return_202_with_location_header() throws Exception {
        UUID txId = UUID.randomUUID();
        when(handler.handle(any())).thenReturn(
                new SubmitTransactionHandler.SubmitResult(txId));

        TransactionRequest request = new TransactionRequest(
                txId,
                "acc-123",
                new BigDecimal("150.00"),
                "USD",
                "merchant-1",
                40.4168, -3.7038,
                Instant.parse("2026-07-22T10:00:00Z"),
                "idem-key-1"
        );

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location",
                        "/api/v1/transactions/" + txId + "/status"))
                .andExpect(content().string(""));
    }

    @Test
    void should_return_400_when_accountId_missing() throws Exception {
        String invalidJson = """
                {
                    "transactionId": "%s",
                    "amount": 150.00,
                    "currency": "USD",
                    "timestamp": "2026-07-22T10:00:00Z",
                    "idempotencyKey": "key-1"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_amount_negative() throws Exception {
        String invalidJson = """
                {
                    "transactionId": "%s",
                    "accountId": "acc-123",
                    "amount": -50.00,
                    "currency": "USD",
                    "timestamp": "2026-07-22T10:00:00Z",
                    "idempotencyKey": "key-2"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_currency_invalid_length() throws Exception {
        String invalidJson = """
                {
                    "transactionId": "%s",
                    "accountId": "acc-123",
                    "amount": 100.00,
                    "currency": "USDOLLAR",
                    "timestamp": "2026-07-22T10:00:00Z",
                    "idempotencyKey": "key-3"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
