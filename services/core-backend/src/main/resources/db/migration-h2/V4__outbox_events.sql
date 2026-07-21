CREATE SCHEMA IF NOT EXISTS outbox;

CREATE TABLE IF NOT EXISTS outbox.outbox_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload        JSON NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at        TIMESTAMP,
    retry_count    INT NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox.outbox_events (status, created_at);
