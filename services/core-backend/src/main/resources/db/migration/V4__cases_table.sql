-- cases.cases: fraud case audit row created by the case-events consumer
-- (ADR-001 §Hexagonal, ADR-004 §4.4 case-creation decision, ADR-007 §7.5
-- audit log). Schema-per-module: `cases` is owned by the Cases module inside
-- Core Backend (services/core-backend). No cross-schema FKs per ADR-002.
CREATE TABLE IF NOT EXISTS cases.cases (
    id                UUID PRIMARY KEY,
    transaction_id    UUID NOT NULL,
    account_id        VARCHAR(255) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    score             INT NOT NULL,
    recommendation    VARCHAR(20) NOT NULL,
    triggered_rules   JSONB NOT NULL DEFAULT '[]'::jsonb,
    opened_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolved_by       VARCHAR(255),
    resolution_notes  TEXT,
    correlation_id    UUID,
    trace_id          CHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_cases_transaction_id
    ON cases.cases (transaction_id);

CREATE INDEX IF NOT EXISTS idx_cases_status_opened_at
    ON cases.cases (status, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_cases_account_opened_at
    ON cases.cases (account_id, opened_at DESC);
