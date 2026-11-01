ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);
CREATE UNIQUE INDEX uq_users_google_subject ON users(google_subject) WHERE google_subject IS NOT NULL;

CREATE TABLE google_auth_flows (
    state_hash VARCHAR(64) PRIMARY KEY,
    nonce VARCHAR(128) NOT NULL,
    code_verifier VARCHAR(128) NOT NULL,
    exchange_secret_hash VARCHAR(64) NOT NULL,
    return_uri VARCHAR(1024) NOT NULL,
    link_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    callback_used BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_hash VARCHAR(64) UNIQUE,
    subject VARCHAR(255),
    email VARCHAR(254),
    email_authoritative BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_google_auth_flows_expiry ON google_auth_flows(expires_at);