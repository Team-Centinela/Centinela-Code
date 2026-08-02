package com.centinela.serverless.adapter.in.consumer;

import com.centinela.serverless.application.ScoreTransactionService;
import com.centinela.serverless.domain.model.FraudDecision;
import com.centinela.serverless.domain.model.Recommendation;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageIdempotencyService;
import com.centinela.serverless.infrastructure.idempotency.ReceivedMessageRepository;
import com.centinela.serverless.infrastructure.observability.TraceparentPropagator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionsRawConsumer}, isolated by faking the
 * downstream collaborators with Mockito. Spring Cloud Stream binder wiring
 * is exercised separately via {@code @SpringBootTest}.
 */
class TransactionsRawConsumerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void missingMessageIdHeaderFailsFast() {
        ReceivedMessageIdempotencyService idem = mock(ReceivedMessageIdempotencyService.class);
        ScoreTransactionService score = mock(ScoreTransactionService.class);

        TransactionsRawConsumer consumer = buildConsumer(idem, score);
        assertThatThrownBy(() -> consumer.handle(MessageBuilder.withPayload("{}").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId header is required");

        verify(idem, never()).claim(anyString(), anyString(), any(UUID.class));
    }

    @Test
    void duplicateDoneOutcomeSkipsScore() {
        ReceivedMessageIdempotencyService idem = mock(ReceivedMessageIdempotencyService.class);
        when(idem.claim(eq(CONSUMER_NAME), eq("msg-1"), any(UUID.class)))
                .thenReturn(claim(ReceivedMessageIdempotencyService.Outcome.DUPLICATE_DONE));
        ScoreTransactionService score = mock(ScoreTransactionService.class);

        buildConsumer(idem, score).handle(message("msg-1"));
        verify(score, never()).score(any(), anyString());
    }

    @Test
    void inflightAndRaceOutcomesBothSkipScore() {
        for (ReceivedMessageIdempotencyService.Outcome skip : List.of(
                ReceivedMessageIdempotencyService.Outcome.INFLIGHT_OTHER,
                ReceivedMessageIdempotencyService.Outcome.RACE_LOST)) {
            ReceivedMessageIdempotencyService idem = mock(ReceivedMessageIdempotencyService.class);
            when(idem.claim(eq(CONSUMER_NAME), anyString(), any(UUID.class)))
                    .thenReturn(claim(skip));
            ScoreTransactionService score = mock(ScoreTransactionService.class);

            buildConsumer(idem, score).handle(message("msg-" + skip));
            verify(score, never()).score(any(), anyString());
        }
    }

    @Test
    void claimedOutcomeCallsScoreWithClaimedRow() {
        ReceivedMessageRepository.Row row = new ReceivedMessageRepository.Row(
                "msg-1", CONSUMER_NAME, "RECEIVED", Instant.now());

        ReceivedMessageIdempotencyService idem = mock(ReceivedMessageIdempotencyService.class);
        when(idem.claim(eq(CONSUMER_NAME), eq("msg-1"), any(UUID.class)))
                .thenReturn(new ReceivedMessageIdempotencyService.ClaimResult(
                        ReceivedMessageIdempotencyService.Outcome.CLAIMED, row));

        ScoreTransactionService score = mock(ScoreTransactionService.class);
        when(score.score(eq(row), anyString()))
                .thenReturn(new FraudDecision(UUID.randomUUID(), 0, List.of(), Recommendation.APPROVE, 70, Instant.now()));

        buildConsumer(idem, score).handle(message("msg-1"));
        verify(score).score(eq(row), anyString());
    }

    private static final String CONSUMER_NAME = "serverless-engine.transactions-raw";
    private static final String PAYLOAD = "{\"transactionId\":\"00000000-0000-0000-0000-000000000001\","
            + "\"accountId\":\"acct-1\",\"amount\":100.00,\"currency\":\"USD\","
            + "\"timestamp\":\"2026-07-26T10:00:00Z\"}";

    private static ReceivedMessageIdempotencyService.ClaimResult claim(
            ReceivedMessageIdempotencyService.Outcome outcome) {
        return new ReceivedMessageIdempotencyService.ClaimResult(outcome, null);
    }

    private static Message<String> message(String messageId) {
        return MessageBuilder.withPayload(PAYLOAD).setHeader("messageId", messageId).build();
    }

    private static TransactionsRawConsumer buildConsumer(ReceivedMessageIdempotencyService idem,
                                                        ScoreTransactionService score) {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.spanBuilder()).thenReturn(mock(Span.Builder.class));
        when(tracer.currentSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.tag(anyString(), any(Long.class))).thenReturn(span);
        when(span.start()).thenReturn(span);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(tracer.withSpan(span)).thenReturn(scope);

        TraceparentPropagator propagator = mock(TraceparentPropagator.class);
        when(propagator.startConsumerSpan(anyString(), any())).thenReturn(span);

        return new TransactionsRawConsumer(OBJECT_MAPPER, idem, score, propagator, tracer);
    }
}
