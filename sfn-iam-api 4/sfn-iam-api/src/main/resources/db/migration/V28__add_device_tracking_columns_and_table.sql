-- ============================================================
-- Device Tracking Enhancement for Login Audit
-- Adds device-related columns to login_audit_event
-- Creates user_device_login table for tracking user-device relationships
-- ============================================================

-- ============================================================
-- Step 1: Add device tracking columns to login_audit_event
-- ============================================================

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS device_id VARCHAR(100);

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(100);

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS device_name VARCHAR(200);

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS browser_type VARCHAR(100);

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS os_info VARCHAR(100);

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS is_new_device BOOLEAN DEFAULT false;

ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS is_trusted_device BOOLEAN DEFAULT false;

-- ============================================================
-- Step 2: Create indexes for device-based queries
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_login_audit_device
    ON login_audit_event(device_id);

CREATE INDEX IF NOT EXISTS idx_login_audit_fingerprint
    ON login_audit_event(device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_device
    ON login_audit_event(tenant_id, device_id);

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_fingerprint
    ON login_audit_event(tenant_id, device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_login_audit_user_device
    ON login_audit_event(tenant_id, user_id, device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_login_audit_new_device
    ON login_audit_event(tenant_id, is_new_device) WHERE is_new_device = true;

-- ============================================================
-- Step 3: Create user_device_login table
-- ============================================================

CREATE TABLE IF NOT EXISTS user_device_login (
    id BIGSERIAL PRIMARY KEY,

    -- Tenant and user identification
    tenant_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    username VARCHAR(100),

    -- Device identification
    device_id VARCHAR(100),
    device_fingerprint VARCHAR(100) NOT NULL,
    device_name VARCHAR(200),
    device_type VARCHAR(50),
    browser_type VARCHAR(100),
    os_info VARCHAR(100),
    user_agent VARCHAR(500),

    -- Login history
    first_login_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    last_ip_address VARCHAR(45),
    last_location VARCHAR(200),
    login_count INTEGER NOT NULL DEFAULT 1,
    failed_login_count INTEGER DEFAULT 0,

    -- Status and trust
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_trusted BOOLEAN DEFAULT false,
    trusted_at TIMESTAMP,
    trusted_by VARCHAR(100),

    -- Block information
    blocked_at TIMESTAMP,
    blocked_by VARCHAR(100),
    block_reason VARCHAR(500),

    -- Session tracking
    last_session_id VARCHAR(100),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    -- Unique constraint: one record per user-device combination per tenant
    CONSTRAINT uk_udl_user_device UNIQUE (user_id, device_fingerprint, tenant_id)
);

-- ============================================================
-- Step 4: Create indexes for user_device_login table
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_udl_tenant
    ON user_device_login(tenant_id);

CREATE INDEX IF NOT EXISTS idx_udl_user
    ON user_device_login(user_id);

CREATE INDEX IF NOT EXISTS idx_udl_device
    ON user_device_login(device_id);

CREATE INDEX IF NOT EXISTS idx_udl_fingerprint
    ON user_device_login(device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_udl_last_login
    ON user_device_login(last_login_at DESC);

CREATE INDEX IF NOT EXISTS idx_udl_status
    ON user_device_login(status);

CREATE INDEX IF NOT EXISTS idx_udl_tenant_user
    ON user_device_login(tenant_id, user_id);

CREATE INDEX IF NOT EXISTS idx_udl_tenant_status
    ON user_device_login(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_udl_trusted
    ON user_device_login(tenant_id, is_trusted) WHERE is_trusted = true;

-- ============================================================
-- Step 5: Add comments for documentation
-- ============================================================

COMMENT ON COLUMN login_audit_event.device_id IS 'Unique device identifier';
COMMENT ON COLUMN login_audit_event.device_fingerprint IS 'Browser/device fingerprint for device tracking';
COMMENT ON COLUMN login_audit_event.device_name IS 'Human-readable device name';
COMMENT ON COLUMN login_audit_event.browser_type IS 'Browser type (Chrome, Firefox, Safari, etc.)';
COMMENT ON COLUMN login_audit_event.os_info IS 'Operating system information';
COMMENT ON COLUMN login_audit_event.is_new_device IS 'Whether this is the first login from this device';
COMMENT ON COLUMN login_audit_event.is_trusted_device IS 'Whether the device is marked as trusted';

COMMENT ON TABLE user_device_login IS 'Tracks user-device login relationships and device trust status';

COMMENT ON COLUMN user_device_login.tenant_id IS 'Tenant identifier for multi-tenant isolation';
COMMENT ON COLUMN user_device_login.user_id IS 'User UUID';
COMMENT ON COLUMN user_device_login.username IS 'Username for reference';
COMMENT ON COLUMN user_device_login.device_id IS 'Unique device identifier';
COMMENT ON COLUMN user_device_login.device_fingerprint IS 'Browser/device fingerprint';
COMMENT ON COLUMN user_device_login.device_name IS 'Human-readable device name';
COMMENT ON COLUMN user_device_login.device_type IS 'Device type (desktop, mobile, tablet)';
COMMENT ON COLUMN user_device_login.browser_type IS 'Browser type';
COMMENT ON COLUMN user_device_login.os_info IS 'Operating system information';
COMMENT ON COLUMN user_device_login.user_agent IS 'Full user agent string';
COMMENT ON COLUMN user_device_login.first_login_at IS 'First login timestamp from this device';
COMMENT ON COLUMN user_device_login.last_login_at IS 'Most recent login timestamp';
COMMENT ON COLUMN user_device_login.last_ip_address IS 'IP address of last login';
COMMENT ON COLUMN user_device_login.last_location IS 'Geographic location of last login';
COMMENT ON COLUMN user_device_login.login_count IS 'Total number of successful logins from this device';
COMMENT ON COLUMN user_device_login.failed_login_count IS 'Total number of failed login attempts';
COMMENT ON COLUMN user_device_login.status IS 'Device status: ACTIVE, INACTIVE, BLOCKED, PENDING_VERIFICATION, REVOKED';
COMMENT ON COLUMN user_device_login.is_trusted IS 'Whether the device is trusted by the user/admin';
COMMENT ON COLUMN user_device_login.trusted_at IS 'When the device was trusted';
COMMENT ON COLUMN user_device_login.trusted_by IS 'Who trusted the device';
COMMENT ON COLUMN user_device_login.blocked_at IS 'When the device was blocked';
COMMENT ON COLUMN user_device_login.blocked_by IS 'Who blocked the device';
COMMENT ON COLUMN user_device_login.block_reason IS 'Reason for blocking the device';
COMMENT ON COLUMN user_device_login.last_session_id IS 'Session ID of the last login';
