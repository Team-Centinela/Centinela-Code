CREATE SCHEMA IF NOT EXISTS oltp;

CREATE SCHEMA IF NOT EXISTS outbox;

CREATE TABLE IF NOT EXISTS oltp.transactions (
    id          UUID PRIMARY KEY,
    account_id  VARCHAR(50) NOT NULL,
    amount      NUMERIC(18,2) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    type        VARCHAR(20) NOT NULL,
    merchant_id VARCHAR(50),
    description VARCHAR(255),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON oltp.transactions (account_id);

CREATE TABLE IF NOT EXISTS outbox.outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSON NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at        TIMESTAMP WITH TIME ZONE,
    attempts       INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox.outbox_events (status, created_at);
