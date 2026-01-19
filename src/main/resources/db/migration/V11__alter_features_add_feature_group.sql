-- Add feature_group reference and feature_code to Features table
ALTER TABLE features ADD COLUMN IF NOT EXISTS fk_feature_group_id BIGINT;
ALTER TABLE features ADD COLUMN IF NOT EXISTS feature_code VARCHAR(100);

-- Add foreign key constraint for feature_group
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_feature_group' AND table_name = 'features'
    ) THEN
        ALTER TABLE features ADD CONSTRAINT fk_feature_group
            FOREIGN KEY (fk_feature_group_id) REFERENCES feature_group(pk_feature_group_id);
    END IF;
END $$;

-- Create unique index for feature_code (only for non-null values)
CREATE UNIQUE INDEX IF NOT EXISTS idx_feature_code ON features(feature_code)
    WHERE feature_code IS NOT NULL;

-- Create index for feature_group lookup
CREATE INDEX IF NOT EXISTS idx_feature_group_ref ON features(fk_feature_group_id);

COMMENT ON COLUMN features.feature_code IS 'Unique code for programmatic feature access';
COMMENT ON COLUMN features.fk_feature_group_id IS 'Reference to feature group for categorization';
