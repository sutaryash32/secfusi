-- V22: Add self_managed column to Tenant table
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
-- NOTE: sfn-iam-api has an identical migration (V31). Both services share the same DB table.
--       IF NOT EXISTS guards ensure only the first service to start creates the column.

ALTER TABLE Tenant
    ADD COLUMN IF NOT EXISTS self_managed BOOLEAN NOT NULL DEFAULT FALSE;
