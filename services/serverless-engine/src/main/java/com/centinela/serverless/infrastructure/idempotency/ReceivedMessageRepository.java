package com.centinela.serverless.infrastructure.idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ReceivedMessageRepository {

    /**
     * ADR-003 §3.3.2 — Business status gating with {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     * On PostgreSQL this is a true locked read; on H2 (test runtime) it degrades to a plain
     * read because H2 lacks the {@code SKIP LOCKED} clause. The {@link IdempotencyDialectResolver}
     * picks the right query at call time.
     */
    private static final String PG_LOCK_QUERY = """
            SELECT * FROM %s.received_messages
             WHERE message_id = ? AND consumer = ?
             FOR UPDATE SKIP LOCKED
            """;

    private static final String H2_LOCK_QUERY = """
            SELECT * FROM %s.received_messages
             WHERE message_id = ? AND consumer = ?
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final IdempotencyDialectResolver dialect;

    public ReceivedMessageRepository(JdbcTemplate jdbc, IdempotencyDialectResolver dialect) {
        this.jdbc = jdbc;
        this.dialect = dialect;
    }

    /**
     * Returns the row if no other in-flight instance holds the lock; an empty result set
     * is returned for an absent row or a row currently locked by another worker
     * ({@code SKIP LOCKED} swallows it on PostgreSQL).
     */
    public List<Row> lockForUpdateSkipping(String schema, String messageId, String consumer) {
        String sql = dialect.useSkipLocked()
                ? String.format(PG_LOCK_QUERY, schema)
                : String.format(H2_LOCK_QUERY, schema);
        return jdbc.query(
                sql,
                (rs, i) -> new Row(
                        rs.getString("message_id"),
                        rs.getString("consumer"),
                        rs.getString("status"),
                        rs.getTimestamp("received_at") == null ? null : rs.getTimestamp("received_at").toInstant()),
                messageId, consumer);
    }

    public Row insertIfAbsent(String schema, String messageId, String consumer, UUID transactionId) {
        String sql = dialect.useInsertOnConflict()
                ? "INSERT INTO " + schema + ".received_messages "
                        + " (message_id, consumer, status, received_at, transaction_id) "
                        + " VALUES (?, ?, 'RECEIVED', NOW(), ?) "
                        + " ON CONFLICT (message_id, consumer) DO NOTHING"
                : "MERGE INTO " + schema + ".received_messages "
                        + " (message_id, consumer, status, received_at, transaction_id) "
                        + " KEY (message_id, consumer) VALUES (?, ?, 'RECEIVED', CURRENT_TIMESTAMP, ?)";
        int rows = jdbc.update(sql, messageId, consumer, transactionId);
        if (rows != 1) {
            return null;
        }
        return new Row(messageId, consumer, "RECEIVED", java.time.Instant.now());
    }

    public void markProcessed(String schema, Row row) {
        jdbc.update(
                "UPDATE " + schema + ".received_messages SET status = 'PROCESSED', processed_at = NOW() "
                        + " WHERE message_id = ? AND consumer = ?",
                row.messageId(), row.consumer());
    }

    public record Row(String messageId, String consumer, String status, java.time.Instant receivedAt) {
    }
}
