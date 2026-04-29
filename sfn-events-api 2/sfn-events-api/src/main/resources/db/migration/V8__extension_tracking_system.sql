-- =====================================================
-- V8: Extension Tracking System
-- =====================================================
-- This migration adds:
-- 1. Anonymous device support fields to devices table
-- 2. installed_extensions table for tracking browser extensions
-- 3. extension_events table for extension event audit trail
-- 4. tenant_code to Tenant table for MSI deployment identification
-- =====================================================

-- =====================================================
-- Part 1: Update devices table for anonymous device support
-- =====================================================

-- Note: device_fingerprint already exists in devices table (V1)
-- Add device_token and anonymous device support columns

ALTER TABLE devices ADD COLUMN IF NOT EXISTS device_token VARCHAR(64) UNIQUE;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN DEFAULT FALSE;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS linked_at TIMESTAMP;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS linked_user_id VARCHAR(50);

-- Handle existing devices: they were registered with authentication, so NOT anonymous
-- Existing devices with user_id are authenticated devices
UPDATE devices SET is_anonymous = FALSE WHERE is_anonymous IS NULL;

-- Generate device_token for existing devices that don't have one
-- Uses MD5 concatenation to create a 64-character hex token (no extension required)
UPDATE devices
SET device_token = md5(random()::text || clock_timestamp()::text) ||
                   md5(random()::text || clock_timestamp()::text)
WHERE device_token IS NULL;

-- Create index for device token lookups (if not exists)
CREATE INDEX IF NOT EXISTS idx_device_token ON devices(device_token);

-- Add comments
COMMENT ON COLUMN devices.device_token IS 'Unique token for device identification (used for anonymous device auth)';
COMMENT ON COLUMN devices.is_anonymous IS 'Whether this device was registered before user login. FALSE for existing authenticated devices.';
COMMENT ON COLUMN devices.linked_at IS 'When the anonymous device was linked to a user';
COMMENT ON COLUMN devices.linked_user_id IS 'User ID that this device was linked to';

-- =====================================================
-- Part 2: Create installed_extensions table
-- =====================================================

CREATE TABLE IF NOT EXISTS installed_extensions (
    pk_installed_extension_id   VARCHAR(50) PRIMARY KEY,
    fk_device_id                VARCHAR(50) NOT NULL,
    tenant_id                   VARCHAR(50) NOT NULL,
    user_id                     VARCHAR(50),

    -- Extension identification
    extension_id                VARCHAR(64) NOT NULL,
    extension_name              VARCHAR(255),
    version                     VARCHAR(50),
    description                 TEXT,
    homepage_url                VARCHAR(500),
    store_url                   VARCHAR(500),
    icon_url                    VARCHAR(500),

    -- Permissions (stored as JSONB arrays)
    permissions                 JSONB,
    host_permissions            JSONB,
    optional_permissions        JSONB,

    -- Status & Policy
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    policy_action               VARCHAR(20),
    policy_reason               VARCHAR(500),
    matched_policy_id           VARCHAR(50),
    is_whitelisted              BOOLEAN DEFAULT FALSE,
    is_blacklisted              BOOLEAN DEFAULT FALSE,

    -- Risk Assessment
    risk_level                  VARCHAR(20),
    risk_score                  INTEGER,
    high_risk_permissions       JSONB,

    -- Installation Info
    install_type                VARCHAR(30),
    is_managed                  BOOLEAN DEFAULT FALSE,
    may_disable                 BOOLEAN DEFAULT TRUE,
    offline_enabled             BOOLEAN DEFAULT FALSE,

    -- Timestamps
    first_seen_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    installed_at                TIMESTAMP,
    uninstalled_at              TIMESTAMP,

    -- Audit columns
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_installed_ext_device FOREIGN KEY (fk_device_id)
        REFERENCES devices(device_id) ON DELETE CASCADE,
    CONSTRAINT uk_device_extension UNIQUE (fk_device_id, extension_id),
    CONSTRAINT chk_ext_status CHECK (status IN ('ACTIVE', 'DISABLED', 'BLOCKED', 'WARNING', 'UNINSTALLED', 'UNKNOWN')),
    CONSTRAINT chk_ext_policy_action CHECK (policy_action IS NULL OR policy_action IN ('ALLOW', 'BLOCK', 'WARN')),
    CONSTRAINT chk_ext_risk_level CHECK (risk_level IS NULL OR risk_level IN ('HIGH', 'MEDIUM', 'LOW', 'NONE'))
);

-- Create indexes for installed_extensions
CREATE INDEX IF NOT EXISTS idx_installed_ext_device ON installed_extensions(fk_device_id);
CREATE INDEX IF NOT EXISTS idx_installed_ext_tenant ON installed_extensions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_installed_ext_user ON installed_extensions(user_id);
CREATE INDEX IF NOT EXISTS idx_installed_ext_extension_id ON installed_extensions(extension_id);
CREATE INDEX IF NOT EXISTS idx_installed_ext_status ON installed_extensions(status);
CREATE INDEX IF NOT EXISTS idx_installed_ext_policy_action ON installed_extensions(policy_action);
CREATE INDEX IF NOT EXISTS idx_installed_ext_risk_level ON installed_extensions(risk_level);

-- Add comments
COMMENT ON TABLE installed_extensions IS 'Tracks browser extensions installed on devices';
COMMENT ON COLUMN installed_extensions.extension_id IS 'Chrome/Edge extension ID (e.g., nkbihfbeogaeaoehlefnkodbefgpgknn)';
COMMENT ON COLUMN installed_extensions.permissions IS 'List of permissions from manifest (JSONB array)';
COMMENT ON COLUMN installed_extensions.risk_score IS 'Calculated risk score (0-100) based on permissions';
COMMENT ON COLUMN installed_extensions.policy_action IS 'Policy evaluation result: ALLOW, BLOCK, or WARN';

-- =====================================================
-- Part 3: Create extension_events table
-- =====================================================

CREATE TABLE IF NOT EXISTS extension_events (
    pk_extension_event_id       VARCHAR(50) PRIMARY KEY,
    fk_device_id                VARCHAR(50),
    fk_installed_extension_id   VARCHAR(50),
    tenant_id                   VARCHAR(50) NOT NULL,
    user_id                     VARCHAR(50),
    user_name                   VARCHAR(100),

    -- Extension identification
    extension_id                VARCHAR(64) NOT NULL,
    extension_name              VARCHAR(255),
    extension_version           VARCHAR(50),

    -- Event details
    event_type                  VARCHAR(40) NOT NULL,
    event_timestamp             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_description           VARCHAR(500),

    -- Version change details
    previous_version            VARCHAR(50),
    new_version                 VARCHAR(50),

    -- Policy details
    policy_action               VARCHAR(20),
    policy_reason               VARCHAR(500),
    policy_rule_id              VARCHAR(50),
    policy_name                 VARCHAR(200),

    -- Risk details
    risk_level                  VARCHAR(20),
    risk_score                  INTEGER,
    is_whitelisted              BOOLEAN,
    is_blacklisted              BOOLEAN,

    -- User action
    user_action                 VARCHAR(30),
    user_reason                 VARCHAR(500),

    -- Device context
    ip_address                  VARCHAR(45),
    browser_type                VARCHAR(100),
    browser_version             VARCHAR(50),

    -- Additional data
    details                     JSONB,

    -- Audit columns
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_ext_event_device FOREIGN KEY (fk_device_id)
        REFERENCES devices(device_id) ON DELETE SET NULL,
    CONSTRAINT fk_ext_event_installed_ext FOREIGN KEY (fk_installed_extension_id)
        REFERENCES installed_extensions(pk_installed_extension_id) ON DELETE SET NULL,
    CONSTRAINT chk_ext_event_type CHECK (event_type IN (
        'EXTENSION_INSTALLED', 'EXTENSION_UNINSTALLED', 'EXTENSION_UPDATED',
        'EXTENSION_ENABLED', 'EXTENSION_DISABLED', 'EXTENSION_PERMISSIONS_CHANGED',
        'EXTENSION_BLOCKED', 'EXTENSION_WARNING_SHOWN', 'EXTENSION_WARNING_ACKNOWLEDGED',
        'EXTENSION_SYNC'
    ))
);

-- Create indexes for extension_events
CREATE INDEX IF NOT EXISTS idx_ext_event_device ON extension_events(fk_device_id);
CREATE INDEX IF NOT EXISTS idx_ext_event_tenant ON extension_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ext_event_user ON extension_events(user_id);
CREATE INDEX IF NOT EXISTS idx_ext_event_extension_id ON extension_events(extension_id);
CREATE INDEX IF NOT EXISTS idx_ext_event_type ON extension_events(event_type);
CREATE INDEX IF NOT EXISTS idx_ext_event_timestamp ON extension_events(event_timestamp);
CREATE INDEX IF NOT EXISTS idx_ext_event_policy_action ON extension_events(policy_action);
CREATE INDEX IF NOT EXISTS idx_ext_event_installed_ext ON extension_events(fk_installed_extension_id);

-- Add comments
COMMENT ON TABLE extension_events IS 'Audit trail for extension-related events';
COMMENT ON COLUMN extension_events.event_type IS 'Type of event: INSTALLED, UNINSTALLED, UPDATED, BLOCKED, etc.';
COMMENT ON COLUMN extension_events.user_action IS 'User response to warning: ACKNOWLEDGED, DISMISSED, PROCEEDED, UNINSTALLED';

-- =====================================================
-- Part 4: Create views for analytics
-- =====================================================

-- View: Extension risk summary by tenant
CREATE OR REPLACE VIEW v_extension_risk_summary AS
SELECT
    tenant_id,
    risk_level,
    COUNT(*) as extension_count,
    COUNT(DISTINCT extension_id) as unique_extensions
FROM installed_extensions
WHERE status = 'ACTIVE'
GROUP BY tenant_id, risk_level;

-- View: Daily extension events summary
CREATE OR REPLACE VIEW v_daily_extension_events AS
SELECT
    tenant_id,
    DATE(event_timestamp) as event_date,
    event_type,
    COUNT(*) as event_count
FROM extension_events
WHERE event_timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY tenant_id, DATE(event_timestamp), event_type;

-- View: Most common extensions
CREATE OR REPLACE VIEW v_common_extensions AS
SELECT
    tenant_id,
    extension_id,
    MAX(extension_name) as extension_name,
    COUNT(*) as install_count,
    MAX(risk_level) as max_risk_level
FROM installed_extensions
WHERE status = 'ACTIVE'
GROUP BY tenant_id, extension_id
ORDER BY install_count DESC;

COMMENT ON VIEW v_extension_risk_summary IS 'Summary of extension risk levels by tenant';
COMMENT ON VIEW v_daily_extension_events IS 'Daily counts of extension events for the last 30 days';
COMMENT ON VIEW v_common_extensions IS 'Most commonly installed extensions by tenant';
