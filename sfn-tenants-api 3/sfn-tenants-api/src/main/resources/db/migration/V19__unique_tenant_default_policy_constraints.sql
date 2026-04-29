-- Enforce that each tenant can have at most one ACTIVE default per policy type.
-- Scoped to is_active = TRUE so that old versioned rows (deactivated on edit) are
-- excluded — the versioned-update pattern sets is_active=false on the old row
-- before inserting the new version, so the unique constraint is never violated.

CREATE UNIQUE INDEX IF NOT EXISTS uix_extension_policy_tenant_default
    ON extension_policy (fk_tenant_id)
    WHERE is_tenant_default = TRUE AND is_active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uix_browserpolicy_tenant_default
    ON browserpolicy (fk_tenant_id)
    WHERE is_tenant_default = TRUE AND is_active = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uix_networkpolicy_tenant_default
    ON networkpolicy (fk_tenant_id)
    WHERE is_tenant_default = TRUE AND is_active = TRUE;
