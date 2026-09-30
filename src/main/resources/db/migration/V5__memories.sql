-- LoveOS shared Memories (PostgreSQL)

CREATE TABLE memories (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   uuid          NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    author_id   uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       varchar(200)  NOT NULL,
    date        date          NOT NULL,
    caption     varchar(500),
    location    varchar(160),
    note        varchar(4000),
    favorite    boolean       NOT NULL DEFAULT false,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    CONSTRAINT ck_memories_title CHECK (length(btrim(title)) > 0)
);

CREATE INDEX idx_memories_couple_date ON memories (couple_id, date DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_memories_couple_favorite ON memories (couple_id, favorite)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_memories_couple_deleted ON memories (couple_id, deleted_at);

CREATE TABLE memory_photos (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id   uuid          NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    url         varchar(2048) NOT NULL,
    width       integer,
    height      integer,
    position    integer       NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_memory_photos_position UNIQUE (memory_id, position),
    CONSTRAINT ck_memory_photos_position CHECK (position >= 0)
);

CREATE INDEX idx_memory_photos_order ON memory_photos (memory_id, position);

CREATE TABLE tags (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   uuid         NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    label       varchar(40)  NOT NULL,
    slug        varchar(40)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tags_couple_slug UNIQUE (couple_id, slug),
    CONSTRAINT ck_tags_label CHECK (length(btrim(label)) > 0),
    CONSTRAINT ck_tags_slug CHECK (slug = lower(slug))
);

CREATE INDEX idx_tags_couple ON tags (couple_id);

CREATE TABLE memory_tags (
    memory_id uuid NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    tag_id    uuid NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    PRIMARY KEY (memory_id, tag_id)
);

CREATE INDEX idx_memory_tags_tag ON memory_tags (tag_id);

CREATE TABLE albums (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   uuid          NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    label       varchar(60)   NOT NULL,
    emoji       varchar(8),
    cover_url   varchar(2048),
    system_key  varchar(40),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_albums_couple_label UNIQUE (couple_id, label),
    CONSTRAINT uq_albums_couple_system_key UNIQUE (couple_id, system_key),
    CONSTRAINT ck_albums_label CHECK (length(btrim(label)) > 0)
);

CREATE INDEX idx_albums_couple ON albums (couple_id, created_at);

CREATE TABLE album_memories (
    album_id  uuid    NOT NULL REFERENCES albums (id) ON DELETE CASCADE,
    memory_id uuid    NOT NULL REFERENCES memories (id) ON DELETE CASCADE,
    position  integer NOT NULL DEFAULT 0,
    PRIMARY KEY (album_id, memory_id),
    CONSTRAINT ck_album_memories_position CHECK (position >= 0)
);

CREATE INDEX idx_album_memories_memory ON album_memories (memory_id);