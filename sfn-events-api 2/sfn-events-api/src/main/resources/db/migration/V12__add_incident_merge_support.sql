-- V12: Add merge support to incidents table

ALTER TABLE incidents ADD COLUMN merged_into VARCHAR(36);
ALTER TABLE incidents ADD COLUMN is_merged BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE incidents ADD CONSTRAINT fk_incident_merged_into
    FOREIGN KEY (merged_into) REFERENCES incidents(pk_incident_id);

-- Update status constraint to include MERGED
ALTER TABLE incidents DROP CONSTRAINT IF EXISTS chk_incident_status;
ALTER TABLE incidents ADD CONSTRAINT chk_incident_status
    CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED', 'FALSE_POSITIVE', 'MERGED'));

-- Add escalated_at for escalation rules (Feature 5)
ALTER TABLE incidents ADD COLUMN escalated_at TIMESTAMPTZ;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_incident_merged_into ON incidents(merged_into);
CREATE INDEX IF NOT EXISTS idx_incident_is_merged ON incidents(fk_tenant_id, is_merged);
