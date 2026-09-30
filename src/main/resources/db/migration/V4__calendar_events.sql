-- LoveOS Calendar events (PostgreSQL)

CREATE TABLE calendar_events (
    id                      uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id               uuid         NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    created_by_id           uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    title                   varchar(160) NOT NULL,
    date                    date         NOT NULL,
    starts_at               timestamptz,
    ends_at                 timestamptz,
    location                varchar(160),
    notes                   varchar(2000),
    kind                    varchar(20)  NOT NULL DEFAULT 'CUSTOM',
    repeats_annually        boolean      NOT NULL DEFAULT false,
    reminder_minutes_before integer,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    deleted_at              timestamptz,
    CONSTRAINT ck_calendar_events_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_calendar_events_kind CHECK (kind IN (
        'ANNIVERSARY', 'BIRTHDAY', 'DATE_NIGHT', 'TRIP', 'REMINDER', 'CUSTOM'
    )),
    CONSTRAINT ck_calendar_events_reminder CHECK (
        reminder_minutes_before IS NULL
        OR reminder_minutes_before BETWEEN 0 AND 43200
    ),
    CONSTRAINT ck_calendar_events_times CHECK (
        starts_at IS NULL OR ends_at IS NULL OR ends_at >= starts_at
    )
);

CREATE INDEX idx_calendar_events_couple_date
    ON calendar_events (couple_id, date, id) WHERE deleted_at IS NULL;

CREATE INDEX idx_calendar_events_couple_active
    ON calendar_events (couple_id, deleted_at);