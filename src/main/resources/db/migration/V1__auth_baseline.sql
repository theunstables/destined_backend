-- LoveOS authentication baseline (PostgreSQL)
--
-- Flyway owns this schema. Hibernate runs with ddl-auto=validate and therefore
-- checks, but never changes, the database. Existing pre-Flyway development
-- databases are baselined at version 1; an empty database executes this file.

CREATE TABLE users (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          varchar(254) NOT NULL,
    email_verified boolean      NOT NULL DEFAULT false,
    password_hash  varchar(255),
    display_name   varchar(120),
    nickname       varchar(120),
    pronouns       varchar(60),
    birthday       date,
    photo_url      varchar(1024),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     varchar(64) NOT NULL,
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    replaced_by_id uuid,
    user_agent     varchar(256),
    ip_hash        varchar(64),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

CREATE TABLE email_verification_tokens (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_verif_token_hash ON email_verification_tokens (token_hash);
CREATE INDEX idx_verif_user ON email_verification_tokens (user_id);

CREATE TABLE password_reset_tokens (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_reset_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_reset_user ON password_reset_tokens (user_id);
