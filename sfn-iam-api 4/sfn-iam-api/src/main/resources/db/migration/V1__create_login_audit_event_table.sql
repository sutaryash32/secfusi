-- ============================================================
-- Login Audit Event Table for Unified Audit Logging
-- Shared across microservices: sfn-tenants-api, sfn-iam-api
-- NOTE: This migration is shared. Only run from ONE service.
-- ============================================================

CREATE TABLE IF NOT EXISTS login_audit_event (
    id BIGSERIAL PRIMARY KEY,

    -- Tenant and Realm identification
    tenant_id VARCHAR(50) NOT NULL,
    realm_name VARCHAR(100),

    -- User identification
    user_id VARCHAR(50),
    username VARCHAR(100),
    email VARCHAR(150),

    -- Event details
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    source_service VARCHAR(50),

    -- Request context
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    client_id VARCHAR(100),
    session_id VARCHAR(100),

    -- Result
    success BOOLEAN NOT NULL DEFAULT true,
    error_message VARCHAR(500),
    error_code VARCHAR(50),

    -- Authentication details
    auth_method VARCHAR(50),
    mfa_used BOOLEAN,
    remember_me BOOLEAN,

    -- Additional context
    location VARCHAR(200),
    device_info VARCHAR(200),
    additional_details TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- Indexes for query performance
-- ============================================================

-- Index for tenant-based queries (most common filter)
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant
    ON login_audit_event(tenant_id);

-- Index for user-based queries
CREATE INDEX IF NOT EXISTS idx_login_audit_user
    ON login_audit_event(user_id);

-- Index for username-based queries
CREATE INDEX IF NOT EXISTS idx_login_audit_username
    ON login_audit_event(username);

-- Index for event type filtering
CREATE INDEX IF NOT EXISTS idx_login_audit_event_type
    ON login_audit_event(event_type);

-- Index for time-based queries (critical for audit reports)
CREATE INDEX IF NOT EXISTS idx_login_audit_timestamp
    ON login_audit_event(event_timestamp DESC);

-- Index for IP address lookups (security analysis)
CREATE INDEX IF NOT EXISTS idx_login_audit_ip
    ON login_audit_event(ip_address);

-- Index for source service filtering (unified audit queries)
CREATE INDEX IF NOT EXISTS idx_login_audit_source_service
    ON login_audit_event(source_service);

-- Composite index for common tenant + time range queries
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_timestamp
    ON login_audit_event(tenant_id, event_timestamp DESC);

-- Composite index for tenant + source service queries
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_source
    ON login_audit_event(tenant_id, source_service);

-- Composite index for tenant + event type queries
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_event_type
    ON login_audit_event(tenant_id, event_type);

-- Composite index for tenant + user queries
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_user
    ON login_audit_event(tenant_id, user_id);

-- ============================================================
-- Comments for documentation
-- ============================================================

COMMENT ON TABLE login_audit_event IS 'Unified audit log table for tracking authentication and IAM events across all microservices';

COMMENT ON COLUMN login_audit_event.tenant_id IS 'Tenant identifier for multi-tenant isolation';
COMMENT ON COLUMN login_audit_event.realm_name IS 'Keycloak realm name';
COMMENT ON COLUMN login_audit_event.user_id IS 'User UUID from Keycloak';
COMMENT ON COLUMN login_audit_event.username IS 'Username for the event';
COMMENT ON COLUMN login_audit_event.email IS 'User email address';
COMMENT ON COLUMN login_audit_event.event_type IS 'Type of audit event (LOGIN_SUCCESS, USER_CREATED, ROLE_ASSIGNED, etc.)';
COMMENT ON COLUMN login_audit_event.event_timestamp IS 'When the event occurred';
COMMENT ON COLUMN login_audit_event.source_service IS 'Microservice that logged the event (TENANTS_API, IAM_API, etc.)';
COMMENT ON COLUMN login_audit_event.ip_address IS 'Client IP address (supports IPv4 and IPv6)';
COMMENT ON COLUMN login_audit_event.user_agent IS 'Browser/client user agent string';
COMMENT ON COLUMN login_audit_event.client_id IS 'OAuth2 client ID';
COMMENT ON COLUMN login_audit_event.session_id IS 'Session identifier';
COMMENT ON COLUMN login_audit_event.success IS 'Whether the event was successful';
COMMENT ON COLUMN login_audit_event.error_message IS 'Error message if event failed';
COMMENT ON COLUMN login_audit_event.error_code IS 'Error code if event failed';
COMMENT ON COLUMN login_audit_event.auth_method IS 'Authentication method used (password, otp, sso, etc.)';
COMMENT ON COLUMN login_audit_event.mfa_used IS 'Whether MFA was used';
COMMENT ON COLUMN login_audit_event.remember_me IS 'Whether remember me was selected';
COMMENT ON COLUMN login_audit_event.location IS 'Geographic location (if available)';
COMMENT ON COLUMN login_audit_event.device_info IS 'Device information';
COMMENT ON COLUMN login_audit_event.additional_details IS 'JSON or text with additional context';
COMMENT ON COLUMN login_audit_event.created_at IS 'Record creation timestamp';
