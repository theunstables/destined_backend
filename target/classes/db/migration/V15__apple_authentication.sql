ALTER TABLE users ADD COLUMN apple_subject VARCHAR(255);
CREATE UNIQUE INDEX uq_users_apple_subject ON users(apple_subject) WHERE apple_subject IS NOT NULL;

CREATE TABLE apple_auth_flows (
    state_hash VARCHAR(64) PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL,
    exchange_secret_hash VARCHAR(64) NOT NULL,
    return_uri VARCHAR(1024) NOT NULL,
    link_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    callback_used BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_hash VARCHAR(64) UNIQUE,
    subject VARCHAR(255),
    email VARCHAR(254),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_apple_auth_flows_expiry ON apple_auth_flows(expires_at);