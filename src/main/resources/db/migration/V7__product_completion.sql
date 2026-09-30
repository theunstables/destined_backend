-- LoveOS product-completion safety and preference state (PostgreSQL)

ALTER TABLE couples DROP CONSTRAINT ck_couples_status;
ALTER TABLE couples ADD CONSTRAINT ck_couples_status
    CHECK (status IN ('PENDING', 'CONNECTED', 'PAUSED', 'ARCHIVED'));
ALTER TABLE couples ADD COLUMN paused_at timestamptz;
ALTER TABLE couples ADD COLUMN unpair_requested_by_id uuid REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE couples ADD COLUMN unpair_requested_at timestamptz;
ALTER TABLE couples ADD COLUMN unpair_expires_at timestamptz;
ALTER TABLE couples ADD CONSTRAINT ck_couples_unpair_state CHECK (
    (unpair_requested_at IS NULL AND unpair_expires_at IS NULL)
    OR (unpair_requested_at IS NOT NULL AND unpair_expires_at IS NOT NULL
        AND unpair_expires_at > unpair_requested_at)
);

ALTER TABLE users ADD COLUMN deleted_at timestamptz;

CREATE TABLE notification_preferences (
    user_id        uuid         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled        boolean      NOT NULL DEFAULT true,
    messages       boolean      NOT NULL DEFAULT true,
    occasions      boolean      NOT NULL DEFAULT true,
    memories       boolean      NOT NULL DEFAULT true,
    quiet_start    time,
    quiet_end      time,
    timezone       varchar(64)  NOT NULL DEFAULT 'UTC',
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_quiet_pair CHECK (
      (quiet_start IS NULL) = (quiet_end IS NULL)
    )
);

CREATE TABLE device_tokens (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       varchar(2048) NOT NULL,
    platform    varchar(16)   NOT NULL,
    active      boolean       NOT NULL DEFAULT true,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_tokens_token UNIQUE (token),
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('EXPO', 'IOS', 'ANDROID', 'WEB'))
);
CREATE INDEX idx_device_tokens_user_active ON device_tokens (user_id, active);

CREATE TABLE account_deletion_requests (
    user_id       uuid         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    requested_at  timestamptz  NOT NULL,
    execute_after timestamptz  NOT NULL,
    cancelled_at  timestamptz,
    executed_at   timestamptz,
    CONSTRAINT ck_account_deletion_window CHECK (execute_after > requested_at)
);
CREATE INDEX idx_account_deletion_due
    ON account_deletion_requests (execute_after)
    WHERE cancelled_at IS NULL AND executed_at IS NULL;
