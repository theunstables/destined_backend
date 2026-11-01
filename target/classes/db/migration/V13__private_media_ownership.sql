CREATE TABLE media_assets (
    object_key VARCHAR(64) PRIMARY KEY,
    couple_id UUID NOT NULL REFERENCES couples(id) ON DELETE CASCADE,
    storage_driver VARCHAR(16) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    bytes BIGINT NOT NULL CHECK (bytes > 0)
);
CREATE INDEX idx_media_assets_couple ON media_assets(couple_id);