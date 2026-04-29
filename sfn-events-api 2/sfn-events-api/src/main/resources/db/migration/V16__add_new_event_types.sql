-- V16: Add support for new browser extension event types
--
-- 1. Widen file_operation_type column for longer enum values
--    BLOCKED_EXTENSION_INSTALLED (27), CREDENTIAL_THEFT_BLOCKED (24), EXTENSION_COMPLIANCE_BLOCK (25)
ALTER TABLE events ALTER COLUMN file_operation_type TYPE VARCHAR(30);

-- 2. Add MITRE ATT&CK mapping column for threat intelligence correlation
ALTER TABLE events ADD COLUMN IF NOT EXISTS mitre_mapping jsonb;
