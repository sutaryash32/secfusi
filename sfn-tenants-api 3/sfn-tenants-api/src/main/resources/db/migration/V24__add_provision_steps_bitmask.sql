-- V24: Add provisioning steps bitmask to Tenant table
--
-- Purpose:
--   Track each provisioning step independently using a bitmask.
--   Allows fine-grained resume/repair: on failure, only the missing steps
--   are retried instead of re-running the entire flow.
--
-- Bit definitions (see ProvisionStep.java):
--   0  = REALM_CREATED
--   1  = CLIENT_CREATED
--   2  = ADMIN_USER_CREATED
--   3  = SSO_CONFIGURED
--   4  = ROLES_CREATED
--   5  = ADMIN_GROUP_CREATED
--   6  = USER_GROUP_LINKED
--   7  = AUTH_CONFIG_SAVED
--   8  = POLICIES_CREATED
--   9  = EVENTS_GROUP_CREATED
--   10 = SUBSCRIPTION_CREATED
--   11 = REALM_SETTINGS_APPLIED
--   12 = EMAILS_SENT
--   13 = TENANT_CODE_GENERATED

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS provision_steps_completed INT NOT NULL DEFAULT 0;

-- Backfill: mark all existing ACTIVE tenants as fully provisioned (all bits set)
UPDATE tenant SET provision_steps_completed = 16383 WHERE status = 'ACTIVE';
