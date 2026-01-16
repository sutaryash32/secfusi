-- Create feature_group table for organizing features in the package-feature matrix
CREATE TABLE IF NOT EXISTS feature_group (
    pk_feature_group_id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL UNIQUE,
    group_code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_feature_group_code ON feature_group(group_code);
CREATE INDEX IF NOT EXISTS idx_feature_group_active ON feature_group(is_active);

COMMENT ON TABLE feature_group IS 'Feature groups for organizing features in the package-feature matrix';
COMMENT ON COLUMN feature_group.group_code IS 'Unique code for programmatic access (e.g., SECOPS, AIOPS)';
COMMENT ON COLUMN feature_group.display_order IS 'Order for UI display purposes';
