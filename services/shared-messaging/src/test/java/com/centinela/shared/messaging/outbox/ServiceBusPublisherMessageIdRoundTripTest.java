package com.centinela.shared.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test using Spring Cloud Stream's {@code TestChannelBinderConfiguration}.
 * Asserts that the {@code messageId} set by {@link ServiceBusPublisherImpl} survives
 * the binder hop on the producer side — the in-process equivalent of the SB Emulator
 * round-trip (which the dedicated SB Emulator replay test in #167 / Phase 0.1.2 covers
 * separately, see ADR-011 §11.3).
 *
 * <p>Why the test binder and not Testcontainers + SB Emulator: the SB Emulator image
 * is not available in this environment (no Docker daemon); the dedicated Phase 0.1.2
 * sub-task on Lane-B is the canonical Testcontainers/SB-Emulator test. This test
 * proves the producer-side messageId contract — that the header lands on the wire —
 * which is the change owned by this PR.</p>
 */
class ServiceBusPublisherMessageIdRoundTripTest {

    @Configuration
    @EnableAutoConfiguration
    @Import(TestChannelBinderConfiguration.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void messageIdHeaderSurvivesBinderHopAndEqualsOutboxRowId() {
        UUID outboxRowId = UUID.fromString("33333333-4444-5555-6666-777777777777");

        runner.run(ctx -> {
            StreamBridge bridge = ctx.getBean(StreamBridge.class);
            ServiceBusPublisherImpl publisher = new ServiceBusPublisherImpl(
                    bridge, "transactions-raw", "case-events", "documents-pending", "fraud-evaluation");

            publisher.publish("TransactionReceived", "tx-77", "{\"x\":1}", outboxRowId.toString());

            OutputDestination out = ctx.getBean(OutputDestination.class);
            Message<byte[]> sent = out.receive(1000, "transactions-raw");
            assertThat(sent)
                    .as("message must reach the binder destination")
                    .isNotNull();
            assertThat(sent.getHeaders().get("messageId"))
                    .as("messageId header must equal outbox_events.id (the deterministic rail)")
                    .isEqualTo(outboxRowId.toString());
            assertThat(sent.getHeaders().get("eventType")).isEqualTo("TransactionReceived");
            assertThat(sent.getHeaders().get("aggregateId")).isEqualTo("tx-77");
        });
    }
}
