package com.centinela.ingestion;

import com.centinela.ingestion.infrastructure.api.TransaccionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAcceptValidTransaction() throws Exception {
        TransaccionRequest request = new TransaccionRequest();
        request.setCuentaId(" cuenta-001");
        request.setMonto(new BigDecimal("150.00"));
        request.setMoneda("USD");

        mockMvc.perform(post("/api/v1/transacciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void shouldRejectNullAmount() throws Exception {
        TransaccionRequest request = new TransaccionRequest();
        request.setCuentaId("cuenta-001");
        request.setMonto(null);

        mockMvc.perform(post("/api/v1/transacciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNegativeAmount() throws Exception {
        TransaccionRequest request = new TransaccionRequest();
        request.setCuentaId("cuenta-001");
        request.setMonto(new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/v1/transacciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEmptyCuentaId() throws Exception {
        TransaccionRequest request = new TransaccionRequest();
        request.setCuentaId("");
        request.setMonto(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/transacciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAmountBelowMinimum() throws Exception {
        TransaccionRequest request = new TransaccionRequest();
        request.setCuentaId("cuenta-001");
        request.setMonto(new BigDecimal("0.00"));

        mockMvc.perform(post("/api/v1/transacciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
