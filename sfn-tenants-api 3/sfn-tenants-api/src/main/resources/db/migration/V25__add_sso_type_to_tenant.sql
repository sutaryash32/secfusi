-- V25: Persist ssoType on Tenant table
--
-- Purpose:
--   Store the SSO type (KEYCLOAK, AZURE, APIKEY) requested during tenant creation
--   directly on the tenant row. Previously this was @Transient and only stored in
--   auth_provider_config, which is created later during finalizeTenant.
--   If provisioning failed before auth_provider_config was created, the SSO type
--   was lost and retry would default to KEYCLOAK.

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS sso_type VARCHAR(20);

-- Backfill existing tenants from auth_provider_config
UPDATE tenant t
SET sso_type = apc.sso_type
FROM auth_provider_config apc
WHERE apc.fk_tenant_id = t.tenantid
  AND t.sso_type IS NULL;
