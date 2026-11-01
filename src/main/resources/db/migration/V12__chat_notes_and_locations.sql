ALTER TABLE messages DROP CONSTRAINT ck_messages_kind;
ALTER TABLE messages DROP CONSTRAINT ck_messages_payload;
ALTER TABLE messages ADD CONSTRAINT ck_messages_kind
    CHECK (kind IN ('TEXT', 'PHOTO', 'VOICE', 'VIDEO', 'NOTE', 'LOCATION'));
ALTER TABLE messages ADD CONSTRAINT ck_messages_payload CHECK (
    (kind IN ('TEXT', 'NOTE') AND body IS NOT NULL AND length(btrim(body)) > 0)
    OR (kind IN ('PHOTO', 'VOICE', 'VIDEO', 'LOCATION') AND media_url IS NOT NULL)
);