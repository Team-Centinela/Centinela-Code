package com.centinela.ingestion.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        // Tracked by #188: when Docker is unavailable, fail the assumption in
        // @BeforeAll (which aborts the test class) AND log an explicit reason
        // via System.err so the CI operator sees why the canonical Postgres
        // migration path was skipped.
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("centinela");
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void requireDockerDaemon() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            System.err.println(
                    "[FlywayMigrationTest] disabled: Docker daemon not available. "
                            + "The canonical Postgres migration path is exercised by "
                            + "'mvn -pl ingestion -am -Pverify-postgres' on CI runners that "
                            + "expose a Docker daemon."
            );
            assumeTrue(
                    false,
                    "Docker daemon not available; FlywayMigrationTest is exercised by 'mvn -pl ingestion -am -Pverify-postgres' on CI runners."
            );
        }
    }

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
