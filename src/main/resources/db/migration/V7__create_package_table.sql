-- Create package table (pre-existing table structure)
-- This migration creates the table if it doesn't exist

CREATE TABLE IF NOT EXISTS package (
    pk_package_id BIGSERIAL PRIMARY KEY,
    package_name VARCHAR(255),
    package_type_id BIGINT,
    description VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_package_package_type FOREIGN KEY (package_type_id) REFERENCES package_type(pk_package_type_id)
);

-- Add unique constraint on package name if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_package_name'
    ) THEN
        ALTER TABLE package ADD CONSTRAINT uk_package_name UNIQUE (package_name);
    END IF;
END $$;

-- Create index on package type id
CREATE INDEX IF NOT EXISTS idx_package_type_id ON package(package_type_id);
