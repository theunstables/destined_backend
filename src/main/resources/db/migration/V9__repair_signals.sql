-- LoveOS low-pressure repair signal (PostgreSQL)

CREATE TABLE repair_signals (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    couple_id           uuid        NOT NULL REFERENCES couples (id) ON DELETE CASCADE,
    sender_id           uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    partner_signaled_at timestamptz,
    status              varchar(20) NOT NULL DEFAULT 'OPEN',
    created_at          timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL,
    cancelled_at        timestamptz,
    CONSTRAINT ck_repair_signal_status
        CHECK (status IN ('OPEN', 'MUTUAL', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_repair_signal_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_repair_signal_state CHECK (
      (status = 'OPEN' AND partner_signaled_at IS NULL AND cancelled_at IS NULL)
      OR (status = 'MUTUAL' AND partner_signaled_at IS NOT NULL AND cancelled_at IS NULL)
      OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
      OR status = 'EXPIRED'
    )
);

-- At most one currently meaningful signal. Expiry is materialized to EXPIRED by
-- the transactional service before a replacement is created.
CREATE UNIQUE INDEX uq_repair_signals_active_couple
    ON repair_signals (couple_id)
    WHERE status IN ('OPEN', 'MUTUAL');
CREATE INDEX idx_repair_signals_couple_created
    ON repair_signals (couple_id, created_at DESC);
