package com.centinela.serverless.infrastructure.idempotency;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivedMessageIdempotencyServiceUnitTest {

    @Test
    void duplicateDoneWhenRowAlreadyProcessed() {
        StubRepository repo = new StubRepository()
                .withExistingRow(new ReceivedMessageRepository.Row("msg-1", "consumer", "PROCESSED", null));
        ReceivedMessageIdempotencyService service = new ReceivedMessageIdempotencyService(repo);
        service.setSchema("oltp");
        var result = service.claim("consumer", "msg-1", UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(ReceivedMessageIdempotencyService.Outcome.DUPLICATE_DONE);
        assertThat(result.row()).isNull();
        assertThat(result.claimed()).isFalse();
    }

    @Test
    void inflightOtherWhenRowExistsButNotProcessed() {
        StubRepository repo = new StubRepository()
                .withExistingRow(new ReceivedMessageRepository.Row("msg-2", "consumer", "RECEIVED", null));
        ReceivedMessageIdempotencyService service = new ReceivedMessageIdempotencyService(repo);
        service.setSchema("oltp");
        var result = service.claim("consumer", "msg-2", UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(ReceivedMessageIdempotencyService.Outcome.INFLIGHT_OTHER);
        assertThat(result.row()).isNull();
    }

    @Test
    void claimedWhenNoRowAndInsertSucceeds() {
        StubRepository repo = new StubRepository();
        ReceivedMessageIdempotencyService service = new ReceivedMessageIdempotencyService(repo);
        service.setSchema("oltp");
        var result = service.claim("consumer", "msg-3", UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(ReceivedMessageIdempotencyService.Outcome.CLAIMED);
        assertThat(result.claimed()).isTrue();
        assertThat(result.row()).isNotNull();
        assertThat(result.row().messageId()).isEqualTo("msg-3");
        assertThat(result.row().consumer()).isEqualTo("consumer");
        assertThat(result.row().status()).isEqualTo("RECEIVED");
    }

    @Test
    void raceLostWhenInsertReturnsZero() {
        StubRepository repo = new StubRepository().withInsertReturningNull();
        ReceivedMessageIdempotencyService service = new ReceivedMessageIdempotencyService(repo);
        service.setSchema("oltp");
        var result = service.claim("consumer", "msg-4", UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(ReceivedMessageIdempotencyService.Outcome.RACE_LOST);
        assertThat(result.row()).isNull();
    }

    /** Test double — short-circuits the JDBC layer to exercise the outcome logic. */
    static final class StubRepository extends ReceivedMessageRepository {
        private ReceivedMessageRepository.Row existingRow;
        private boolean returnNullOnInsert;

        StubRepository() {
            super(null, new IdempotencyDialectResolver(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:t", "sa", "")));
        }

        StubRepository withExistingRow(ReceivedMessageRepository.Row row) {
            this.existingRow = row;
            return this;
        }

        StubRepository withInsertReturningNull() {
            this.returnNullOnInsert = true;
            return this;
        }

        @Override
        public List<Row> lockForUpdateSkipping(String schema, String messageId, String consumer) {
            return existingRow == null ? List.of() : List.of(existingRow);
        }

        @Override
        public Row insertIfAbsent(String schema, String messageId, String consumer, UUID transactionId) {
            return returnNullOnInsert ? null : new Row(messageId, consumer, "RECEIVED", java.time.Instant.now());
        }
    }
}
