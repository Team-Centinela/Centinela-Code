CREATE TABLE IF NOT EXISTS accounts.account (
    id          VARCHAR(50) PRIMARY KEY,
    owner       VARCHAR(255) NOT NULL,
    currency    VARCHAR(3) NOT NULL,
    balance     NUMERIC(18,2) NOT NULL DEFAULT 0.00,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts.transfers (
    id              UUID PRIMARY KEY,
    from_account_id VARCHAR(50) NOT NULL,
    to_account_id   VARCHAR(50) NOT NULL,
    amount          NUMERIC(18,2) NOT NULL,
    description     VARCHAR(255),
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transfers_from ON accounts.transfers (from_account_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to   ON accounts.transfers (to_account_id);
