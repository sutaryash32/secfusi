-- V21: Add extension_authorized column to events_groups table
--
-- Purpose:
--   Adds a separate gate for browser extension login, independent of the main app login
--   gate (authorized column).
--
-- Behavior:
--   - authorized           = controls main app login + Keycloak essential claim filter
--   - extension_authorized = controls browser extension login (checked by ExtensionAuthController)
--
-- Default policy: when a group is authorized for main login, extension access is granted
--   automatically (extension_authorized=true). Admin can revoke extension access independently
--   via DELETE /events-groups/{id}/authorize-extension without affecting main login.
--
-- NOTE: sfn-iam-api has an identical migration (V30). Both services share the same DB table.
--       IF NOT EXISTS guards ensure only the first service to start creates the column/index.
--       The second service's migration runs as a safe no-op.

-- IF NOT EXISTS: safe to run even if sfn-iam-api already added this column first
ALTER TABLE events_groups
    ADD COLUMN IF NOT EXISTS extension_authorized BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: all currently authorized groups get extension access enabled
UPDATE events_groups
SET extension_authorized = TRUE
WHERE authorized = TRUE
  AND group_type = 'AZURE_GROUP'
  AND extension_authorized = FALSE;

-- IF NOT EXISTS: safe to run even if sfn-iam-api already created this index
CREATE INDEX IF NOT EXISTS idx_events_group_ext_authorized
    ON events_groups (tenant_id, extension_authorized, is_active);
