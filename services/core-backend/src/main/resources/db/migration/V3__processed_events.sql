CREATE TABLE IF NOT EXISTS outbox.processed_events (
    consumer        VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, idempotency_key)
);
