-- ============================================================
-- Add source_service column for unified audit logging
-- This allows tracking which microservice logged each event
-- ============================================================

-- Add source_service column if it doesn't exist
ALTER TABLE login_audit_event
ADD COLUMN IF NOT EXISTS source_service VARCHAR(50);

-- Create index for source_service filtering
CREATE INDEX IF NOT EXISTS idx_login_audit_source_service
    ON login_audit_event(source_service);

-- Create composite index for tenant + source service queries
CREATE INDEX IF NOT EXISTS idx_login_audit_tenant_source
    ON login_audit_event(tenant_id, source_service);

-- Add comment for documentation
COMMENT ON COLUMN login_audit_event.source_service IS 'Microservice that logged the event (TENANTS_API, IAM_API, GATEWAY_API, etc.)';
