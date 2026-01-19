-- Create package_feature_mapping table for the package-feature matrix
CREATE TABLE IF NOT EXISTS package_feature_mapping (
    pk_mapping_id BIGSERIAL PRIMARY KEY,
    fk_package_id BIGINT NOT NULL,
    fk_feature_group_id BIGINT NOT NULL,
    fk_feature_id BIGINT NOT NULL,
    fk_access_level_id BIGINT NOT NULL,
    fk_retention_period_id BIGINT,
    is_enabled BOOLEAN DEFAULT true,
    custom_config JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_pfm_package FOREIGN KEY (fk_package_id)
        REFERENCES package(pk_package_id) ON DELETE CASCADE,
    CONSTRAINT fk_pfm_feature_group FOREIGN KEY (fk_feature_group_id)
        REFERENCES feature_group(pk_feature_group_id),
    CONSTRAINT fk_pfm_feature FOREIGN KEY (fk_feature_id)
        REFERENCES features(pk_feature_id),
    CONSTRAINT fk_pfm_access_level FOREIGN KEY (fk_access_level_id)
        REFERENCES access_level(pk_access_level_id),
    CONSTRAINT fk_pfm_retention FOREIGN KEY (fk_retention_period_id)
        REFERENCES retention_period(pk_retention_period_id),
    CONSTRAINT uk_package_feature UNIQUE (fk_package_id, fk_feature_id)
);

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_pfm_package ON package_feature_mapping(fk_package_id);
CREATE INDEX IF NOT EXISTS idx_pfm_feature ON package_feature_mapping(fk_feature_id);
CREATE INDEX IF NOT EXISTS idx_pfm_feature_group ON package_feature_mapping(fk_feature_group_id);
CREATE INDEX IF NOT EXISTS idx_pfm_enabled ON package_feature_mapping(is_enabled);
CREATE INDEX IF NOT EXISTS idx_pfm_package_enabled ON package_feature_mapping(fk_package_id, is_enabled);

COMMENT ON TABLE package_feature_mapping IS 'Package-feature matrix defining feature access per subscription package';
COMMENT ON COLUMN package_feature_mapping.custom_config IS 'JSON configuration for feature-specific settings';
