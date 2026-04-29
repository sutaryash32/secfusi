-- Deduplicate tenant default policies.
-- Caused by a race condition in createTenantDefaultPolicy() where concurrent
-- requests could each insert a tenant default before the other's commit was visible.
-- Keeps the most recently created row (by created_at) per tenant, deletes the rest.

DELETE FROM extension_policy
WHERE is_tenant_default = TRUE
  AND pk_extension_policy_id NOT IN (
    SELECT DISTINCT ON (fk_tenant_id) pk_extension_policy_id
    FROM extension_policy
    WHERE is_tenant_default = TRUE
    ORDER BY fk_tenant_id, created_at DESC
  );

DELETE FROM browserpolicy
WHERE is_tenant_default = TRUE
  AND pk_browserpolicy_id NOT IN (
    SELECT DISTINCT ON (fk_tenant_id) pk_browserpolicy_id
    FROM browserpolicy
    WHERE is_tenant_default = TRUE
    ORDER BY fk_tenant_id, created_at DESC
  );

DELETE FROM networkpolicy
WHERE is_tenant_default = TRUE
  AND pk_networkpolicy_id NOT IN (
    SELECT DISTINCT ON (fk_tenant_id) pk_networkpolicy_id
    FROM networkpolicy
    WHERE is_tenant_default = TRUE
    ORDER BY fk_tenant_id, created_at DESC
  );
