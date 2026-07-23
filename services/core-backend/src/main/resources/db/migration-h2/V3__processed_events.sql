CREATE TABLE IF NOT EXISTS processed_events (
    consumer        VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer, idempotency_key)
);
