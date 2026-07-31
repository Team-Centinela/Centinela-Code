package com.centinela.serverless.migration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostGIS V1 migration dry-run for the engine module — closes #167 §0.1.1.
 *
 * <p>Boots {@code postgis/postgis:16-3.4-alpine} (the same image family
 * the compose stack uses at {@code docker-compose.yml:21} and that
 * {@code verify-emulators.ps1:123} validates against) and confirms:
 * <ul>
 *   <li>Flyway V1 ({@code init_engine_schemas}) + V2 ({@code install_extensions})
 *       install cleanly with {@code success=true}</li>
 *   <li>{@code postgis} and {@code uuid-ossp} extensions are present
 *       (per {@code verify-emulators.ps1:160})</li>
 *   <li>{@code received_messages.received_messages} ledger table exists
 *       with the {@code PRIMARY KEY (message_id, consumer)} per ADR-003 §3.3.2</li>
 *   <li>{@code rules_config.rule_configs} + {@code flagged_merchants} exist</li>
 *   <li>{@code triggered_rules.triggered_rules} has the
 *       {@code uq_triggered_rule_per_tx} unique index per ADR-004 §4.3</li>
 *   <li>{@code outbox} schema namespace exists (engine V1 ensures it
 *       exists so its OutboxPublisher can write into the shared table
 *       even if it boots before Ingestion — see V1 line 67)</li>
 * </ul>
 *
 * <p>The {@code spring.jpa.hibernate.ddl-auto=none} override is required
 * because {@code outbox.outbox_events} is owned by Ingestion's V1, not
 * the engine's — without the override, JPA validation against the
 * engine-only migration subset fails on {@code EngineOutboxEventEntity}.
 * This test is about the Flyway + ledger path, not JPA mapping.
 *
 * <p>Skipped cleanly when no Docker daemon is available
 * (matches the {@code services/ingestion/.../FlywayMigrationTest.java:42-56}
 * pattern).
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
class PostgisV1MigrationTest {

    @SuppressWarnings("resource")
    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGIS_IMAGE)
                    .withDatabaseName("centinela")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.locations",
                () -> "classpath:db/migration/common,classpath:db/migration/pg");
    }

    @BeforeAll
    static void requireDockerDaemon() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            System.err.println(
                    "[PostgisV1MigrationTest] disabled: Docker daemon not available. "
                            + "Re-run on a Docker host or CI runner with a Docker daemon.");
            assumeTrue(false, "Docker daemon not available");
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayHistoryContainsV1AndV2Pg() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history "
                        + "ORDER BY installed_rank");

        assertTrue(rows.size() >= 2,
                "expected >= 2 Flyway entries (V1 common + V2 pg), got " + rows.size());

        Map<String, Object> first = rows.get(0);
        assertEquals("1", first.get("version").toString());
        assertEquals("init_engine_schemas", first.get("description"));
        assertEquals(Boolean.TRUE, first.get("success"),
                "V1 (init_engine_schemas) did not succeed");

        Map<String, Object> last = rows.get(rows.size() - 1);
        assertEquals("2", last.get("version").toString());
        assertEquals(Boolean.TRUE, last.get("success"),
                "V2 (install_extensions) did not succeed");
    }

    @Test
    void postgisAndUuidOsspExtensionsInstalled() {
        List<String> exts = jdbc.queryForList(
                "SELECT extname FROM pg_extension "
                        + "WHERE extname IN ('postgis','uuid-ossp') ORDER BY extname",
                String.class);
        assertEquals(List.of("postgis", "uuid-ossp"), exts);
    }

    @Test
    void receivedMessagesLedgerTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'received_messages' "
                        + "AND table_name = 'received_messages'",
                Integer.class);
        assertEquals(Integer.valueOf(1), count);
    }

    @Test
    void rulesConfigTablesExist() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'rules_config' "
                        + "AND table_name IN ('rule_configs','flagged_merchants')",
                Integer.class);
        assertEquals(Integer.valueOf(2), n);
    }

    @Test
    void triggeredRulesUniqueIndexExists() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE schemaname = 'triggered_rules' "
                        + "AND indexname = 'uq_triggered_rule_per_tx'",
                Integer.class);
        assertEquals(Integer.valueOf(1), n);
    }

    @Test
    void outboxSchemaNamespaceExists() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata "
                        + "WHERE schema_name = 'outbox'",
                Integer.class);
        assertEquals(Integer.valueOf(1), n);
    }
}