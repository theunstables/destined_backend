-- Current locations are deliberately ephemeral, not a movement history.
-- A user owns one replaceable fix; pausing sharing deletes it immediately.
CREATE TABLE current_location_shares (
    user_id       uuid             PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    couple_id     uuid             NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    latitude      double precision NOT NULL,
    longitude     double precision NOT NULL,
    accuracy_m    double precision,
    battery       integer,
    activity      varchar(80),
    shared_until  timestamptz,
    updated_at    timestamptz      NOT NULL DEFAULT now(),
    CONSTRAINT ck_location_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_location_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_location_accuracy CHECK (accuracy_m IS NULL OR accuracy_m >= 0),
    CONSTRAINT ck_location_battery CHECK (battery IS NULL OR battery BETWEEN 0 AND 100)
);

CREATE INDEX idx_current_location_shares_couple
    ON current_location_shares (couple_id);
