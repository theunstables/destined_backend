-- LoveOS durable couple messaging (PostgreSQL)

CREATE TABLE messages (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id   uuid          NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    sender_id   uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        varchar(16)   NOT NULL,
    body        varchar(4000),
    media_url   varchar(2048),
    duration_ms integer,
    reply_to_id uuid          REFERENCES messages (id) ON DELETE SET NULL,
    client_id   varchar(64),
    pinned      boolean       NOT NULL DEFAULT false,
    sent_at     timestamptz   NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    CONSTRAINT uq_messages_couple_client UNIQUE (couple_id, client_id),
    CONSTRAINT ck_messages_kind CHECK (kind IN ('TEXT', 'PHOTO', 'VOICE', 'VIDEO')),
    CONSTRAINT ck_messages_payload CHECK (
      (kind = 'TEXT' AND body IS NOT NULL AND length(btrim(body)) > 0)
      OR (kind <> 'TEXT' AND media_url IS NOT NULL)
    ),
    CONSTRAINT ck_messages_duration CHECK (duration_ms IS NULL OR duration_ms BETWEEN 0 AND 3600000),
    CONSTRAINT ck_messages_client_id CHECK (client_id IS NULL OR length(client_id) BETWEEN 8 AND 64)
);

CREATE INDEX idx_messages_couple_sent ON messages (couple_id, sent_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_messages_couple_pinned ON messages (couple_id, sent_at DESC)
    WHERE deleted_at IS NULL AND pinned;

CREATE TABLE message_reactions (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id  uuid         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    emoji       varchar(8)   NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_reactions_actor UNIQUE (message_id, user_id, emoji),
    CONSTRAINT ck_message_reactions_emoji CHECK (length(btrim(emoji)) BETWEEN 1 AND 8)
);

CREATE INDEX idx_message_reactions_message ON message_reactions (message_id, created_at);

CREATE TABLE message_receipts (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    uuid         NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id       uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delivered_at  timestamptz,
    read_at       timestamptz,
    CONSTRAINT uq_message_receipts_recipient UNIQUE (message_id, user_id),
    CONSTRAINT ck_message_receipts_order CHECK (
      read_at IS NULL OR (delivered_at IS NOT NULL AND read_at >= delivered_at)
    )
);

CREATE INDEX idx_message_receipts_pending_delivery ON message_receipts (user_id, message_id)
    WHERE delivered_at IS NULL;
CREATE INDEX idx_message_receipts_pending_read ON message_receipts (user_id, message_id)
    WHERE read_at IS NULL;
