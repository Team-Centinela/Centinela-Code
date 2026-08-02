package com.centinela.cases.adapter.in.messaging;

import com.centinela.cases.application.CreateCaseFromEvaluationUseCase;
import com.centinela.cases.application.CreateCaseFromEvaluationUseCase.FraudEvaluationCompletedEvent;
import com.centinela.cases.application.CreateCaseFromEvaluationUseCase.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseEventsConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void malformedPayloadIsSkippedWithoutThrowing() {
        CreateCaseFromEvaluationUseCase useCase = mock(CreateCaseFromEvaluationUseCase.class);
        CaseEventsConsumer consumer = new CaseEventsConsumer(MAPPER, useCase);
        Message<String> message = MessageBuilder.withPayload("{not-json").build();

        consumer.handle(message);

        verify(useCase, never()).execute(any());
    }

    @Test
    void parsesBlockEnvelopeAndExecutesUseCase() throws Exception {
        CreateCaseFromEvaluationUseCase useCase = mock(CreateCaseFromEvaluationUseCase.class);
        when(useCase.execute(any())).thenReturn(Outcome.CREATED);
        CaseEventsConsumer consumer = new CaseEventsConsumer(MAPPER, useCase);

        String envelope = "{\"eventType\":\"FraudEvaluationCompleted\",\"eventVersion\":\"1.0\","
                + "\"eventTime\":\"2026-07-31T12:00:00Z\",\"correlationId\":\"" + UUID.randomUUID() + "\","
                + "\"transactionId\":\"" + UUID.randomUUID() + "\",\"accountId\":\"acct-1\","
                + "\"recommendation\":\"BLOCK\",\"score\":80,\"triggeredRules\":[]}";

        Message<String> message = MessageBuilder.withPayload(envelope).build();
        consumer.handle(message);

        ArgumentCaptor<FraudEvaluationCompletedEvent> captor =
                ArgumentCaptor.forClass(FraudEvaluationCompletedEvent.class);
        verify(useCase).execute(captor.capture());
        FraudEvaluationCompletedEvent passed = captor.getValue();
        assertThat(passed.recommendation()).isEqualTo("BLOCK");
        assertThat(passed.score()).isEqualTo(80);
        assertThat(passed.triggeredRules()).isEmpty();
    }

    @Test
    void missingAggregateIdHeaderAndPayloadFallsBackToSkipping() {
        CreateCaseFromEvaluationUseCase useCase = mock(CreateCaseFromEvaluationUseCase.class);
        CaseEventsConsumer consumer = new CaseEventsConsumer(MAPPER, useCase);

        String envelope = "{\"recommendation\":\"FLAG\",\"score\":40}";
        consumer.handle(MessageBuilder.withPayload(envelope).build());

        verify(useCase, never()).execute(any());
    }
}
