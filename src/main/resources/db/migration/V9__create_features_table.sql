-- Create Features table (pre-existing table structure)
-- Note: Table name is case-sensitive "Features"

CREATE TABLE IF NOT EXISTS features (
    pk_feature_id BIGSERIAL PRIMARY KEY,
    feature_name VARCHAR(255),
    feature_code VARCHAR(100),
    description TEXT,
    feature_scope VARCHAR(255),
    feature_type VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    fk_feature_group_id BIGINT,
    created_by BIGINT,
    last_modified_by BIGINT,
    created_timestamp TIMESTAMP,
    last_modified_timestamp TIMESTAMP,
    CONSTRAINT fk_features_feature_group FOREIGN KEY (fk_feature_group_id) REFERENCES feature_group(pk_feature_group_id)
);

-- Add unique constraint on feature code if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_feature_code'
    ) THEN
        ALTER TABLE features ADD CONSTRAINT uk_feature_code UNIQUE (feature_code);
    END IF;
END $$;

-- Create index on feature group id
CREATE INDEX IF NOT EXISTS idx_features_feature_group_id ON features(fk_feature_group_id);

-- Create index on feature code for faster lookups
CREATE INDEX IF NOT EXISTS idx_features_feature_code ON features(feature_code);

-- Create index on is_active for filtering
CREATE INDEX IF NOT EXISTS idx_features_is_active ON features(is_active);   