CREATE SCHEMA IF NOT EXISTS outbox;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS oltp;

-- Outbox schema: reliable event publishing per ADR-003
CREATE TABLE outbox.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox.outbox_events (status, created_at) WHERE status = 'PENDING';

-- Auth schema: API key authentication per ADR-006
CREATE TABLE auth.api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE auth.idempotency_keys (
    key_hash VARCHAR(64) NOT NULL,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    request_hash VARCHAR(64) NOT NULL,
    response_status INT,
    response_body JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (key_hash)
);

CREATE INDEX idx_idempotency_created ON auth.idempotency_keys (created_at);

-- OLTP schema: transaction ingestion per ADR-002
CREATE TABLE oltp.transactions (
    id UUID NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    merchant_id VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    "timestamp" TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY HASH (account_id);

CREATE TABLE oltp.transactions_p0 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE oltp.transactions_p1 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE oltp.transactions_p2 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE oltp.transactions_p3 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE oltp.transactions_p4 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE oltp.transactions_p5 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE oltp.transactions_p6 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE oltp.transactions_p7 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE oltp.transactions_p8 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE oltp.transactions_p9 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE oltp.transactions_p10 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE oltp.transactions_p11 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE oltp.transactions_p12 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE oltp.transactions_p13 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE oltp.transactions_p14 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE oltp.transactions_p15 PARTITION OF oltp.transactions FOR VALUES WITH (MODULUS 16, REMAINDER 15);

-- Supports "recent activity for account" queries; ordered index on
-- (account_id, "timestamp" DESC). The original `WHERE created_at > NOW()
-- - INTERVAL '7 days'` partial-index predicate was rejected by PostgreSQL
-- (`functions in index predicate must be marked IMMUTABLE`, because NOW()
-- is VOLATILE), so the predicate was dropped; the 7-day filter is applied
-- post-index-scan by the planner.
CREATE INDEX idx_transactions_account_recent ON oltp.transactions (account_id, "timestamp" DESC);
