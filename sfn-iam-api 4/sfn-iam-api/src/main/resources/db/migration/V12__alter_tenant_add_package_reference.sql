-- Add package reference to Tenant table for subscription management
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS fk_package_id BIGINT;

-- Add foreign key constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_tenant_package' AND table_name = 'tenant'
    ) THEN
        ALTER TABLE tenant ADD CONSTRAINT fk_tenant_package
            FOREIGN KEY (fk_package_id) REFERENCES package(pk_package_id);
    END IF;
END $$;

-- Create index for package lookup
CREATE INDEX IF NOT EXISTS idx_tenant_package ON tenant(fk_package_id);

COMMENT ON COLUMN tenant.fk_package_id IS 'Reference to subscription package for feature access control';
