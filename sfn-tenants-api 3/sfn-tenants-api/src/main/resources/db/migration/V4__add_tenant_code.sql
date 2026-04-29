-- ============================================
-- V4: Add tenant_code column to tenant table
-- Purpose: Unique tenant code for MSI deployment identification
-- Used by browser extension to identify tenant without requiring login
-- Example values: "ACME-2024", "CORP-MSI"
-- ============================================

-- Step 1: Add tenant_code column (without UNIQUE constraint first)
ALTER TABLE tenant ADD COLUMN tenant_code VARCHAR(20);

-- Step 2: Generate friendly tenant codes for existing tenants
-- Format: FIRST4CHARS-SEQ (e.g., ACME-001, SECU-002)
-- Uses first 4 letters of tenant name + sequence number to handle duplicates
UPDATE tenant t
SET tenant_code = sub.generated_code
FROM (
    SELECT
        tenantid,
        CONCAT(
            UPPER(SUBSTRING(REGEXP_REPLACE(COALESCE(tenant_name, 'TNNT'), '[^a-zA-Z]', '', 'g'), 1, 4)),
            '-',
            LPAD(ROW_NUMBER() OVER (
                PARTITION BY UPPER(SUBSTRING(REGEXP_REPLACE(COALESCE(tenant_name, 'TNNT'), '[^a-zA-Z]', '', 'g'), 1, 4))
                ORDER BY created_at, tenantid
            )::TEXT, 3, '0')
        ) AS generated_code
    FROM tenant
    WHERE tenant_code IS NULL
) sub
WHERE t.tenantid = sub.tenantid AND t.tenant_code IS NULL;

-- Step 3: Add UNIQUE constraint after data is populated
ALTER TABLE tenant ADD CONSTRAINT uk_tenant_code UNIQUE (tenant_code);

-- Step 4: Create index for fast lookups
CREATE INDEX idx_tenant_code ON tenant(tenant_code);

-- ============================================
-- Usage:
-- Browser extension can identify tenant using:
--   GET /api/tenants/tenant-config?tenantCode=ACME-2024
--
-- This is useful for MSI deployments where the tenant code
-- is embedded in the extension configuration.
--
-- Note: Existing tenants will have auto-generated codes (e.g., ACME-001).
-- These can be updated manually via API if needed.
-- ============================================

COMMENT ON COLUMN tenant.tenant_code IS 'Unique tenant code for MSI deployment identification (e.g., ACME-2024)';
