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

-- Received Messages schema: consumer-side idempotency per ADR-003 §3.3
CREATE SCHEMA IF NOT EXISTS received_messages;

CREATE TABLE received_messages.received_messages (
    message_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_received_status ON received_messages.received_messages (status, received_at);

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
-- Outbox Pattern). The CREATE is idempotent so when Ingestion's V1 lands first the
-- statements are no-ops on the shared B1ms instance.
CREATE SCHEMA IF NOT EXISTS outbox;

CREATE TABLE IF NOT EXISTS outbox.outbox_events (
    id               UUID PRIMARY KEY,
    event_type       VARCHAR(255) NOT NULL,
    aggregate_id     VARCHAR(255) NOT NULL,
    aggregate_type   VARCHAR(255) NOT NULL,
    payload          JSONB NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at          TIMESTAMP WITH TIME ZONE,
    attempts         INT NOT NULL DEFAULT 0,
    last_attempt_at  TIMESTAMP WITH TIME ZONE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox.outbox_events (status, created_at);
