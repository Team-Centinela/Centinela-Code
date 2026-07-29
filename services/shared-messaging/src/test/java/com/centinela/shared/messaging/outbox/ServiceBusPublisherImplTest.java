package com.centinela.shared.messaging.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceBusPublisherImplTest {

    private StreamBridge streamBridge;
    private ServiceBusPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        streamBridge = mock(StreamBridge.class);
        when(streamBridge.send(any(String.class), any(Message.class))).thenReturn(true);
        publisher = new ServiceBusPublisherImpl(
                streamBridge,
                "transactions-raw",
                "case-events",
                "documents-pending",
                "fraud-evaluation");
    }

    @Test
    void publishesMessageWithCallerSuppliedMessageIdHeader() {
        UUID outboxRowId = UUID.fromString("11111111-2222-3333-4444-555555555555");

        publisher.publish("TransactionReceived", "tx-42", "{\"k\":\"v\"}", outboxRowId.toString());

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("transactions-raw"), captor.capture());
        Message<?> sent = captor.getValue();

        assertThat(sent.getHeaders().get(ServiceBusPublisherImpl.MESSAGE_ID_HEADER))
                .isEqualTo(outboxRowId.toString());
    }

    @Test
    void messageIdEqualsOutboxRowIdExactlyNotSynthesized() {
        UUID outboxRowId = UUID.randomUUID();

        publisher.publish("TransactionReceived", "tx-1", "{}", outboxRowId.toString());

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(any(String.class), captor.capture());

        Object header = captor.getValue().getHeaders().get(ServiceBusPublisherImpl.MESSAGE_ID_HEADER);
        assertThat(header).isEqualTo(outboxRowId.toString());
        assertThat(header.toString()).matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    @Test
    void routesTransactionReceivedToTransactionsRawBinding() {
        publisher.publish("TransactionReceived", "tx-1", "{}", UUID.randomUUID().toString());
        verify(streamBridge).send(eq("transactions-raw"), any(Message.class));
    }

    @Test
    void routesFraudEvaluationCompletedToFraudEvaluationBinding() {
        publisher.publish("FraudEvaluationCompleted", "tx-1", "{}", UUID.randomUUID().toString());
        verify(streamBridge).send(eq("fraud-evaluation"), any(Message.class));
    }

    @Test
    void routesCaseCreatedToCaseEventsBinding() {
        publisher.publish("CaseCreated", "case-1", "{}", UUID.randomUUID().toString());
        verify(streamBridge).send(eq("case-events"), any(Message.class));
    }

    @Test
    void routesDocumentUploadedToDocumentsPendingBinding() {
        publisher.publish("DocumentUploaded", "doc-1", "{}", UUID.randomUUID().toString());
        verify(streamBridge).send(eq("documents-pending"), any(Message.class));
    }

    @Test
    void rejectsNullMessageId() {
        assertThatThrownBy(() -> publisher.publish("TransactionReceived", "tx-1", "{}", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    @Test
    void rejectsBlankMessageId() {
        assertThatThrownBy(() -> publisher.publish("TransactionReceived", "tx-1", "{}", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    @Test
    void rejectsEmptyMessageId() {
        assertThatThrownBy(() -> publisher.publish("TransactionReceived", "tx-1", "{}", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }
}
