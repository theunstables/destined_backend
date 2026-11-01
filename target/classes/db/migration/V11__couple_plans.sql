CREATE TABLE couple_plans (
    couple_id UUID PRIMARY KEY REFERENCES couples(id) ON DELETE CASCADE,
    payload TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);