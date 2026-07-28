-- Rules Config schema: rule definitions and flagged merchant lists
CREATE SCHEMA IF NOT EXISTS rules_config;

CREATE TABLE rules_config.rule_configs (
    id UUID DEFAULT ${uuid-func}() PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSON NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE rules_config.flagged_merchants (
    id UUID DEFAULT ${uuid-func}() PRIMARY KEY,
    merchant_id VARCHAR(255) NOT NULL UNIQUE,
    risk_category VARCHAR(50) NOT NULL,
    flagged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reason TEXT
);

-- Received Messages ledger: consumer-side idempotency per ADR-003 §3.3.2.
-- The schema must match the SQL emitted by
-- infrastructure/idempotency/ReceivedMessageRepository.java:
--   message_id is VARCHAR (Service Bus message-id), consumer is VARCHAR
--   (logical consumer name e.g. "serverless-engine.transactions-raw"),
--   status cycles RECEIVED -> PROCESSED. The PRIMARY KEY (message_id,
--   consumer) is what the ON CONFLICT clause references.
CREATE SCHEMA IF NOT EXISTS received_messages;

CREATE TABLE received_messages.received_messages (
    message_id      VARCHAR(128) NOT NULL,
    consumer        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    received_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE,
    transaction_id  UUID,
    PRIMARY KEY (message_id, consumer)
);

CREATE INDEX idx_received_messages_status ON received_messages.received_messages (status, received_at);

-- Triggered Rules schema: per-FR evaluation evidence per ADR-004
CREATE SCHEMA IF NOT EXISTS triggered_rules;

CREATE TABLE triggered_rules.triggered_rules (
    id UUID DEFAULT ${uuid-func}() PRIMARY KEY,
    transaction_id UUID NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    score INT NOT NULL,
    raw_evidence JSON NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_triggered_rule_per_tx ON triggered_rules.triggered_rules (transaction_id, rule_code);

CREATE INDEX idx_triggered_transaction ON triggered_rules.triggered_rules (transaction_id, evaluated_at DESC);

-- Outbox schema: shared with Ingestion API and Core Backend per ADR-002 (single PostgreSQL
-- across all services) and ADR-003 §3.2 (every service that publishes events runs the
-- Outbox Pattern). The table itself is owned by the Ingestion service (its V1 creates
-- `outbox.outbox_events` with `id DEFAULT gen_random_uuid()` and the canonical column
-- ordering). Serverless-engine only ensures the schema namespace exists here so its
-- Outbox Publisher can write into the shared table at runtime even if it boots before
-- ingestion; the table itself is not re-created (Tracked by #190 — the previous
-- `CREATE TABLE IF NOT EXISTS outbox.outbox_events` race-tripped on
-- `pg_type_typname_nsp_index` because PostgreSQL's `CREATE TABLE IF NOT EXISTS` is
-- not concurrency-safe across parallel Flyway runs).
CREATE SCHEMA IF NOT EXISTS outbox;
