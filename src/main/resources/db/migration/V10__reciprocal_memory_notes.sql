-- Private reciprocal notes attached to shared Memories.
-- The existing memories.note column remains the couple's shared note.

CREATE TABLE memory_private_notes (
    memory_id   uuid          NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    note        varchar(4000) NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (memory_id, user_id),
    CONSTRAINT ck_memory_private_notes_note CHECK (length(btrim(note)) > 0)
);

CREATE INDEX idx_memory_private_notes_user ON memory_private_notes (user_id, updated_at DESC);
