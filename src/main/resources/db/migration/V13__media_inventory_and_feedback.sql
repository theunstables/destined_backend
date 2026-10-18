-- Provider-independent media inventory and user feedback.

CREATE TABLE media_objects (
    object_key    varchar(128)  PRIMARY KEY,
    couple_id     uuid          NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    uploaded_by   uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    public_url    varchar(2048) NOT NULL,
    content_type  varchar(100)  NOT NULL,
    bytes         bigint        NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_objects_bytes CHECK (bytes > 0)
);

CREATE INDEX idx_media_objects_couple_type
    ON media_objects (couple_id, content_type, created_at DESC);

CREATE TABLE user_feedback (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    couple_id     uuid          REFERENCES couples (id) ON DELETE SET NULL,
    topic         varchar(20)   NOT NULL,
    message       varchar(4000) NOT NULL,
    status        varchar(20)   NOT NULL DEFAULT 'NEW',
    created_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_feedback_topic CHECK (topic IN ('PROBLEM', 'IDEA', 'OTHER')),
    CONSTRAINT ck_user_feedback_status CHECK (status IN ('NEW', 'REVIEWED', 'CLOSED')),
    CONSTRAINT ck_user_feedback_message CHECK (length(btrim(message)) >= 12)
);

CREATE INDEX idx_user_feedback_created ON user_feedback (created_at DESC);
