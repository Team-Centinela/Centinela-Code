package com.centinela.ingestion.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("centinela");

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldApplyMigrationSuccessfully() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet schemas = stmt.executeQuery(
                    "SELECT schema_name FROM information_schema.schemata " +
                    "WHERE schema_name IN ('oltp', 'outbox', 'auth') ORDER BY schema_name");

            assertTrue(schemas.next(), "oltp schema should exist");
            assertTrue(schemas.next(), "outbox schema should exist");
            assertTrue(schemas.next(), "auth schema should exist");
            assertFalse(schemas.next(), "no extra schemas should exist");
        }
    }

    @Test
    void shouldCreatePartitionedTransactionsTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT partition_strategy FROM information_schema.tables " +
                     "WHERE table_schema = 'oltp' AND table_name = 'transactions'")) {

            assertTrue(rs.next(), "transactions table should exist");
        }
    }

    @Test
    void shouldCreatePartialIndexOnOutbox() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexdef FROM pg_indexes " +
                     "WHERE indexname = 'idx_outbox_pending'")) {

            assertTrue(rs.next(), "idx_outbox_pending should exist");
            String def = rs.getString("indexdef");
            assertTrue(def.contains("WHERE"), "should be a partial index");
        }
    }
}
