CREATE SCHEMA IF NOT EXISTS oltp;

CREATE SCHEMA IF NOT EXISTS outbox;

CREATE TABLE IF NOT EXISTS outbox.outbox_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    attempts        INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox.outbox_events (status, created_at)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS oltp.received_messages (
    message_id      VARCHAR(128) NOT NULL,
    consumer        VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    transaction_id  UUID,
    PRIMARY KEY (message_id, consumer)
);

CREATE INDEX IF NOT EXISTS idx_received_messages_status
    ON oltp.received_messages (status, received_at);
