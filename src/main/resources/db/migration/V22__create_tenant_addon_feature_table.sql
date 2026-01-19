-- Create tenant_addon_feature table for add-on features purchased by tenants beyond their base package

CREATE TABLE IF NOT EXISTS tenant_addon_feature (
    pk_addon_id BIGSERIAL PRIMARY KEY,
    fk_tenant_id VARCHAR(255) NOT NULL,
    fk_feature_id BIGINT NOT NULL,
    fk_access_level_id BIGINT NOT NULL,
    fk_retention_period_id BIGINT,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    start_date DATE,
    end_date DATE,
    is_trial BOOLEAN DEFAULT FALSE,
    custom_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Add unique constraint to prevent duplicate addon for same tenant-feature combination
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_tenant_addon_feature'
    ) THEN
        ALTER TABLE tenant_addon_feature
            ADD CONSTRAINT uk_tenant_addon_feature UNIQUE (fk_tenant_id, fk_feature_id);
    END IF;
END $$;

-- Add foreign key constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_addon_tenant'
    ) THEN
        ALTER TABLE tenant_addon_feature
            ADD CONSTRAINT fk_addon_tenant
            FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_addon_feature'
    ) THEN
        ALTER TABLE tenant_addon_feature
            ADD CONSTRAINT fk_addon_feature
            FOREIGN KEY (fk_feature_id) REFERENCES features(pk_feature_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_addon_access_level'
    ) THEN
        ALTER TABLE tenant_addon_feature
            ADD CONSTRAINT fk_addon_access_level
            FOREIGN KEY (fk_access_level_id) REFERENCES access_level(pk_access_level_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_addon_retention_period'
    ) THEN
        ALTER TABLE tenant_addon_feature
            ADD CONSTRAINT fk_addon_retention_period
            FOREIGN KEY (fk_retention_period_id) REFERENCES retention_period(pk_retention_period_id);
    END IF;
END $$;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_addon_tenant_id ON tenant_addon_feature(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_addon_feature_id ON tenant_addon_feature(fk_feature_id);
CREATE INDEX IF NOT EXISTS idx_addon_is_enabled ON tenant_addon_feature(is_enabled);
CREATE INDEX IF NOT EXISTS idx_addon_start_date ON tenant_addon_feature(start_date);
CREATE INDEX IF NOT EXISTS idx_addon_end_date ON tenant_addon_feature(end_date);
CREATE INDEX IF NOT EXISTS idx_addon_tenant_enabled ON tenant_addon_feature(fk_tenant_id, is_enabled);

-- Add comment on table
COMMENT ON TABLE tenant_addon_feature IS 'Stores add-on features purchased by tenants beyond their base package subscription';
