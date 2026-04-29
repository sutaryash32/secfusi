-- Create package_type table (pre-existing table structure)
-- This migration creates the table if it doesn't exist

CREATE TABLE IF NOT EXISTS package_type (
    pk_package_type_id BIGSERIAL PRIMARY KEY,
    package_type_name VARCHAR(255)  
);

-- Add unique constraint on package type name if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_package_type_name'
    ) THEN
        ALTER TABLE package_type ADD CONSTRAINT uk_package_type_name UNIQUE (package_type_name);
    END IF;
END $$;
