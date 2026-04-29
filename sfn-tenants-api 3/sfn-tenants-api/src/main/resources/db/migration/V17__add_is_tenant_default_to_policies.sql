-- Add is_tenant_default column to all policy tables
-- Supports per-tenant editable default policies

ALTER TABLE browserpolicy
    ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE networkpolicy
    ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE extension_policy
    ADD COLUMN IF NOT EXISTS is_tenant_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for fast lookup: one tenant default per tenant per policy type
CREATE INDEX IF NOT EXISTS idx_browserpolicy_tenant_default
    ON browserpolicy (fk_tenant_id) WHERE is_tenant_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_networkpolicy_tenant_default
    ON networkpolicy (fk_tenant_id) WHERE is_tenant_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_extension_policy_tenant_default
    ON extension_policy (fk_tenant_id) WHERE is_tenant_default = TRUE;
