-- LoveOS Story and important dates (PostgreSQL)

CREATE TABLE stories (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id     uuid        NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    met_date      date,
    met_precision varchar(20),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_stories_couple UNIQUE (couple_id),
    CONSTRAINT ck_stories_met_precision
        CHECK (met_precision IS NULL OR met_precision IN ('EXACT', 'MONTH_YEAR', 'YEAR_ONLY')),
    CONSTRAINT ck_stories_met_pair
        CHECK ((met_date IS NULL) = (met_precision IS NULL))
);

CREATE TABLE story_moments (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id   uuid         NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    kind       varchar(20)  NOT NULL,
    date       date,
    location   varchar(120),
    note       varchar(2000),
    photo_url  varchar(2048),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_story_moments_kind UNIQUE (story_id, kind),
    CONSTRAINT ck_story_moments_kind
        CHECK (kind IN ('FIRST_DATE', 'BECAME_US', 'FIRST_MEMORY'))
);

CREATE INDEX idx_story_moments_story ON story_moments (story_id);

CREATE TABLE key_dates (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id  uuid        NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    kind       varchar(30) NOT NULL,
    date       date        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_key_dates_kind UNIQUE (couple_id, kind),
    CONSTRAINT ck_key_dates_kind CHECK (kind IN (
        'ANNIVERSARY', 'FIRST_DATE', 'FIRST_MEETING',
        'FOUNDER_BIRTHDAY', 'MEMBER_BIRTHDAY'
    ))
);

CREATE INDEX idx_key_dates_couple ON key_dates (couple_id);