package com.centinela.serverless.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Switches between PostgreSQL and H2 query dialects for the idempotency ledger.
 *
 * <p>The {@code SELECT ... FOR UPDATE SKIP LOCKED} clause (ADR-003 §3.3.2) and the
 * {@code INSERT ... ON CONFLICT DO NOTHING} are PostgreSQL idioms. H2 supports the
 * latter via {@code MERGE INTO ... KEY} but does not recognize the SKIP LOCKED hint.
 * Choosing the right dialect at runtime keeps the test double identical to production
 * without requiring Testcontainers for the unit-tier test.</p>
 */
@Component
public class IdempotencyDialectResolver {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyDialectResolver.class);

    private final boolean skipLocked;
    private final boolean insertOnConflict;

    public IdempotencyDialectResolver(DataSource dataSource) {
        boolean postgres = false;
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            String name = md.getDatabaseProductName();
            postgres = name != null && name.toLowerCase().contains("postgres");
        } catch (Exception e) {
            // Silent degradation here would silently disable SKIP LOCKED in
            // production and collapse the §3.3.2 guarantee during a brief
            // Postgres outage. Surface it so an operator can see why
            // dedup has degraded to a non-locking read.
            log.warn("Could not introspect DataSource product name; falling back to "
                    + "non-PostgreSQL SQL dialect (SELECT without SKIP LOCKED, "
                    + "MERGE-based upsert). ADR-003 §3.3.2 dedup is DEGRADED until "
                    + "the DataSource is reachable.", e);
            postgres = false;
        }
        this.skipLocked = postgres;
        this.insertOnConflict = postgres;
    }

    public boolean useSkipLocked() {
        return skipLocked;
    }

    public boolean useInsertOnConflict() {
        return insertOnConflict;
    }
}
