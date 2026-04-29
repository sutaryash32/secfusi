-- Create access_level table for defining feature access levels
CREATE TABLE IF NOT EXISTS access_level (
    pk_access_level_id BIGSERIAL PRIMARY KEY,
    level_name VARCHAR(50) NOT NULL UNIQUE,
    level_code VARCHAR(30) NOT NULL UNIQUE,
    level_value INTEGER NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT true
);

-- Seed access levels with ordered values for comparison
INSERT INTO access_level (level_name, level_code, level_value, description) VALUES
('No', 'NO', 0, 'Feature not available'),
('Coming Soon', 'COMING_SOON', 1, 'Feature planned for future release'),
('Limited', 'LIMITED', 2, 'Feature available with limitations'),
('Basic', 'BASIC', 3, 'Basic feature access'),
('Yes', 'YES', 4, 'Full feature access'),
('Advanced', 'ADVANCED', 5, 'Advanced feature access with additional capabilities'),
('Advanced + Custom', 'ADVANCED_CUSTOM', 6, 'Advanced features with custom configuration options'),
('Advanced + Scheduled', 'ADVANCED_SCHEDULED', 7, 'Advanced features with scheduled automation');

COMMENT ON TABLE access_level IS 'Access level definitions for package-feature mappings';
COMMENT ON COLUMN access_level.level_value IS 'Numeric value for comparison: higher value = more access';
