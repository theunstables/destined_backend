-- Durable state for the remaining frontend domains.
-- Plans are stored as a versioned couple document because the UI edits one
-- cohesive workspace whose nested itinerary/capsule/letter shapes evolve
-- together. Preferences are user-scoped: partners may choose independently.

CREATE TABLE couple_plan_states (
    couple_id   uuid        PRIMARY KEY REFERENCES couples (id) ON DELETE CASCADE,
    version     bigint      NOT NULL DEFAULT 1,
    state       jsonb       NOT NULL,
    updated_by  uuid        NOT NULL REFERENCES users (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_couple_plan_states_object CHECK (jsonb_typeof(state) = 'object')
);

CREATE TABLE user_preferences (
    user_id     uuid        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    version     bigint      NOT NULL DEFAULT 1,
    preferences jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_preferences_object CHECK (jsonb_typeof(preferences) = 'object')
);

ALTER TABLE memories
    ADD COLUMN video_uri varchar(2048),
    ADD COLUMN voice_uri varchar(2048),
    ADD COLUMN voice_duration_ms integer,
    ADD CONSTRAINT ck_memories_voice_attachment CHECK (
        (voice_uri IS NULL AND voice_duration_ms IS NULL)
        OR (voice_uri IS NOT NULL AND voice_duration_ms IS NOT NULL AND voice_duration_ms > 0)
    );
