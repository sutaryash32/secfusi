-- Create FeatureTypes table (pre-existing table structure)
-- Note: Table name is case-sensitive "FeatureTypes"

CREATE TABLE IF NOT EXISTS feature_types (
    feature_type_id BIGSERIAL PRIMARY KEY,
    feature_type_name VARCHAR(255),
    feature_type_code VARCHAR(255),
    is_active BOOLEAN DEFAULT true
);

-- Add unique constraint on feature type code if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_feature_type_code'
    ) THEN
        ALTER TABLE feature_types ADD CONSTRAINT uk_feature_type_code UNIQUE (feature_type_code);
    END IF;
END $$;
