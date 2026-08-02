package com.centinela.ingestion.infrastructure.web;

import com.centinela.ingestion.application.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("POST /api/v1/transactions — Integration Tests")
class TransactionEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private TransactionRequest buildValidRequest() {
        return new TransactionRequest(
                UUID.randomUUID(),
                "ACC-001",
                new BigDecimal("250.75"),
                "USD",
                "MERCH-42",
                40.4168,
                -3.7038,
                Instant.parse("2026-07-30T14:30:00Z"),
                UUID.randomUUID().toString()
        );
    }

    // ──────────────────────────────────────────────────────────────
    //  Happy path
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful submissions")
    class SuccessfulSubmissions {

        @Test
        @DisplayName("should return 202 Accepted with Location header for valid request")
        void shouldReturn202WithLocation() throws Exception {
            TransactionRequest request = buildValidRequest();

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().string("Location",
                            "/api/v1/transactions/" + request.getTransactionId() + "/status"))
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("should return 202 for request without optional fields (merchantId, lat/lon)")
        void shouldReturn202WithoutOptionalFields() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-002",
                    new BigDecimal("100.00"),
                    "EUR",
                    null,
                    null,
                    null,
                    Instant.parse("2026-07-30T12:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(header().exists("Location"));
        }

        @Test
        @DisplayName("should return 202 for minimal valid request (only required fields)")
        void shouldReturn202ForMinimalRequest() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-MIN",
                        "amount": 1.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-min-1"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept multiple sequential transactions")
        void shouldAcceptMultipleTransactions() throws Exception {
            for (int i = 0; i < 3; i++) {
                TransactionRequest request = buildValidRequest();
                mockMvc.perform(post("/api/v1/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isAccepted());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Validation — required fields
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Required field validation")
    class RequiredFieldValidation {

        @Test
        @DisplayName("should return 400 when transactionId is null")
        void shouldReturn400WhenTransactionIdNull() throws Exception {
            String json = """
                    {
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-1"
                    }
                    """;

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when accountId is blank")
        void shouldReturn400WhenAccountIdBlank() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "",
                        "amount": 100.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-2"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when amount is null")
        void shouldReturn400WhenAmountNull() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-3"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when currency is blank")
        void shouldReturn400WhenCurrencyBlank() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-4"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when timestamp is null")
        void shouldReturn400WhenTimestampNull() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "idempotencyKey": "idem-5"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when idempotencyKey is blank")
        void shouldReturn400WhenIdempotencyKeyBlank() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": ""
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Validation — business rules
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Business rule validation")
    class BusinessRuleValidation {

        @Test
        @DisplayName("should return 400 when amount is negative")
        void shouldReturn400WhenAmountNegative() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": -50.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-neg"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when amount is zero")
        void shouldReturn400WhenAmountZero() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 0.00,
                        "currency": "USD",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-zero"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when currency length is not 3")
        void shouldReturn400WhenCurrencyInvalidLength() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "US",
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-cur"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when latitude is out of range")
        void shouldReturn400WhenLatitudeOutOfRange() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "latitude": 91.0,
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-lat"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when longitude is out of range")
        void shouldReturn400WhenLongitudeOutOfRange() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "longitude": -181.0,
                        "timestamp": "2026-07-30T10:00:00Z",
                        "idempotencyKey": "idem-lon"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when timestamp is in the future")
        void shouldReturn400WhenTimestampFuture() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "timestamp": "2099-12-31T23:59:59Z",
                        "idempotencyKey": "idem-future"
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Content type & malformed JSON
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Content type and malformed JSON")
    class ContentTypeAndMalformedJson {

        @Test
        @DisplayName("should return 415 when Content-Type is text/plain")
        void shouldReturn415WhenWrongContentType() throws Exception {
            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("{}"))
                    .andExpect(status().is(415));
        }

        @Test
        @DisplayName("should return 400 when body is malformed JSON")
        void shouldReturn400WhenMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when body is empty")
        void shouldReturn400WhenEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when body is null")
        void shouldReturn400WhenNullBody() throws Exception {
            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Edge cases — boundary values
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Boundary values")
    class BoundaryValues {

        @Test
        @DisplayName("should accept latitude at exact boundary -90")
        void shouldAcceptLatitudeAtBoundary() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("10.00"),
                    "USD",
                    null,
                    -90.0,
                    0.0,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept latitude at exact boundary +90")
        void shouldAcceptLatitudeAtPositiveBoundary() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("10.00"),
                    "USD",
                    null,
                    90.0,
                    0.0,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept longitude at exact boundary -180")
        void shouldAcceptLongitudeAtBoundary() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("10.00"),
                    "USD",
                    null,
                    0.0,
                    -180.0,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept longitude at exact boundary +180")
        void shouldAcceptLongitudeAtPositiveBoundary() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("10.00"),
                    "USD",
                    null,
                    0.0,
                    180.0,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept very small positive amount")
        void shouldAcceptSmallAmount() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("0.01"),
                    "USD",
                    null,
                    null,
                    null,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept large amount")
        void shouldAcceptLargeAmount() throws Exception {
            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(),
                    "ACC-001",
                    new BigDecimal("999999999.99"),
                    "USD",
                    null,
                    null,
                    null,
                    Instant.parse("2026-07-30T10:00:00Z"),
                    UUID.randomUUID().toString()
            );

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("should accept timestamp at current instant (PastOrPresent)")
        void shouldAcceptCurrentTimestamp() throws Exception {
            String json = """
                    {
                        "transactionId": "%s",
                        "accountId": "ACC-001",
                        "amount": 100.00,
                        "currency": "USD",
                        "timestamp": "%s",
                        "idempotencyKey": "idem-now"
                    }
                    """.formatted(UUID.randomUUID(), Instant.now());

            mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isAccepted());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Actuator endpoints
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Actuator endpoints")
    class ActuatorEndpoints {

        @Test
        @DisplayName("GET /actuator/health should return 200 OK")
        void shouldReturnHealthCheck() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }
}
