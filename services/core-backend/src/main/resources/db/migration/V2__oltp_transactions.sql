CREATE TABLE IF NOT EXISTS oltp.transactions (
    id          UUID PRIMARY KEY,
    account_id  VARCHAR(50) NOT NULL,
    amount      NUMERIC(18,2) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    type        VARCHAR(20) NOT NULL,
    merchant_id VARCHAR(50),
    description VARCHAR(255),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON oltp.transactions (account_id);
