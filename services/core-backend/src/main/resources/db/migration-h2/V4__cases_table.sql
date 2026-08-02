-- H2 mirror of V4__cases_table.sql for application-h2 profile tests.
-- H2 in PostgreSQL mode accepts `JSON` instead of `JSONB` and stores
-- TIMESTAMP WITH TIME ZONE. Keep this mirror in sync with the PG migration.
CREATE TABLE IF NOT EXISTS cases.cases (
    id                UUID PRIMARY KEY,
    transaction_id    UUID NOT NULL,
    account_id        VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    score             INT NOT NULL,
    recommendation    VARCHAR(20) NOT NULL,
    triggered_rules   JSON NOT NULL,
    opened_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolved_by       VARCHAR(255),
    resolution_notes  CLOB,
    correlation_id    UUID,
    trace_id          VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_cases_transaction_id
    ON cases.cases (transaction_id);

CREATE INDEX IF NOT EXISTS idx_cases_status_opened_at
    ON cases.cases (status, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_cases_account_opened_at
    ON cases.cases (account_id, opened_at DESC);
