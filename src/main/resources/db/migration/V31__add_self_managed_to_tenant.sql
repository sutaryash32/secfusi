-- V31: Add self_managed column to Tenant table
--
-- Purpose:
--   Supports MSSP/Master MSSP tenants that also manage their own users directly
--   (act as both a manager of sub-tenants AND an enterprise with own users/groups/policies).
--
-- Behavior:
--   - self_managed = false (default): tenant operates as pure MSSP/Master MSSP
--   - self_managed = true: admin gets dual roles (MSSP ADMIN + ENTERPRISE ADMIN),
--     a default events group is created, and the UI shows a mode switcher.
--
-- NOTE: sfn-tenants-api has an identical migration (V22). Both services share the same DB table.
--       IF NOT EXISTS guard ensures only the first service to start creates the column.

ALTER TABLE Tenant
    ADD COLUMN IF NOT EXISTS self_managed BOOLEAN NOT NULL DEFAULT FALSE;
