-- H2-only fixture for the shared `outbox.outbox_events` table (Tracked by #190).
-- The Postgres canonical migration lives in
-- `services/ingestion/src/main/resources/db/migration/V1__init_ingestion_schemas.sql`
-- and is owned by the Ingestion service. Serverless-engine only inserts into
-- the table at runtime via `JpaOutboxEventAppender`. PostgreSQL's
-- `CREATE TABLE IF NOT EXISTS` is not concurrency-safe across parallel Flyway
-- runs, so the production V1 must run exactly once. The H2 in-memory profile
-- (which boots a single serverless-engine JVM per test) runs the DDL here.
--
-- Column layout matches the H2 fixture convention used by ingestion/core-backend
-- (`JSON` rather than `JSONB`; `TIMESTAMP WITH TIME ZONE` rather than `TIMESTAMPTZ`).
-- Serverless's JPA entity maps `published_at` per ADR-003 §3.2.

CREATE TABLE outbox.outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSON NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP WITH TIME ZONE,
    attempts       INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_pending
    ON outbox.outbox_events (status, created_at);