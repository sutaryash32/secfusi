-- =====================================================
-- V1: Create devices table for device management
-- =====================================================

-- Create devices table
CREATE TABLE IF NOT EXISTS devices (
    device_id           VARCHAR(50) PRIMARY KEY,
    device_name         VARCHAR(200),
    tenant_id           VARCHAR(50) NOT NULL,
    user_id             VARCHAR(50),
    user_name           VARCHAR(100),
    user_agent          VARCHAR(500),
    device_type         VARCHAR(50),
    browser_type        VARCHAR(100),
    extension_version   VARCHAR(50),
    ip_address          VARCHAR(45),
    location            VARCHAR(200),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    first_seen_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    os_info             VARCHAR(200),
    device_fingerprint  VARCHAR(100),

    -- Audit columns (from Auditable)
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),

    -- Constraints
    CONSTRAINT chk_device_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

-- Create indexes for devices table
CREATE INDEX IF NOT EXISTS idx_device_tenant ON devices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_device_user ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_device_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_device_last_seen ON devices(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_device_fingerprint ON devices(device_fingerprint, tenant_id);

-- Add comments
COMMENT ON TABLE devices IS 'Stores registered browser extension devices';
COMMENT ON COLUMN devices.device_id IS 'Unique device identifier (UUID)';
COMMENT ON COLUMN devices.device_fingerprint IS 'Browser fingerprint for device identification';
COMMENT ON COLUMN devices.status IS 'Device status: ACTIVE, INACTIVE, or BLOCKED';
COMMENT ON COLUMN devices.extension_version IS 'Browser extension version installed';
