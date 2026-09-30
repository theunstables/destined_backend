-- LoveOS couple lifecycle (PostgreSQL)

CREATE TABLE couples (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         varchar(255),
    short_name   varchar(100),
    cover_style  varchar(20)  NOT NULL DEFAULT 'DAWN',
    status       varchar(20)  NOT NULL DEFAULT 'PENDING',
    connected_at timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_couples_cover_style CHECK (cover_style IN ('DAWN', 'DUSK', 'NIGHT')),
    CONSTRAINT ck_couples_status CHECK (status IN ('PENDING', 'CONNECTED', 'ARCHIVED'))
);

CREATE TABLE couple_members (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id  uuid        NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       varchar(20) NOT NULL,
    joined_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_couple_members_user UNIQUE (user_id),
    CONSTRAINT uq_couple_members_couple_user UNIQUE (couple_id, user_id),
    CONSTRAINT ck_couple_members_role CHECK (role IN ('FOUNDER', 'MEMBER'))
);

CREATE INDEX idx_couple_members_couple ON couple_members (couple_id);

CREATE TABLE invites (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code           varchar(6)  NOT NULL,
    couple_id      uuid        NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    created_by_id  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status         varchar(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at     timestamptz NOT NULL,
    redeemed_by_id uuid        REFERENCES users (id) ON DELETE SET NULL,
    redeemed_at    timestamptz,
    cancelled_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invites_code UNIQUE (code),
    CONSTRAINT ck_invites_status
        CHECK (status IN ('ACTIVE', 'REDEEMED', 'ACCEPTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_invites_redeemed
        CHECK ((redeemed_by_id IS NULL) = (redeemed_at IS NULL))
);

CREATE UNIQUE INDEX uq_invites_one_active_per_couple
    ON invites (couple_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_invites_couple_status ON invites (couple_id, status);
CREATE INDEX idx_invites_redeemed_by_status ON invites (redeemed_by_id, status);