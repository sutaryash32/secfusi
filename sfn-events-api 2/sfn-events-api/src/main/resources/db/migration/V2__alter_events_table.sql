-- =====================================================
-- V2: Alter events table to add new columns
-- =====================================================

-- Add new columns to events table
ALTER TABLE events ADD COLUMN IF NOT EXISTS event_type VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS fk_device_id VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);
ALTER TABLE events ADD COLUMN IF NOT EXISTS location VARCHAR(200);
ALTER TABLE events ADD COLUMN IF NOT EXISTS title VARCHAR(500);
ALTER TABLE events ADD COLUMN IF NOT EXISTS domain VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS duration_seconds BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS details JSONB;
ALTER TABLE events ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_policy_violation BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS policy_rule_id VARCHAR(50);

-- Add foreign key constraint for device
ALTER TABLE events
    ADD CONSTRAINT fk_events_device
    FOREIGN KEY (fk_device_id)
    REFERENCES devices(device_id)
    ON DELETE SET NULL;

-- Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_events_device ON events(fk_device_id);
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_domain ON events(domain);
CREATE INDEX IF NOT EXISTS idx_events_violation ON events(is_policy_violation);
CREATE INDEX IF NOT EXISTS idx_events_category ON events(category);

-- Add comments for new columns
COMMENT ON COLUMN events.event_type IS 'Type of event: WEBSITE_VISIT, TRACKING_ACTIVITY, FILE_OPERATION, USER_BEHAVIOR, SYSTEM_CONFIGURATION, POLICY_VIOLATION';
COMMENT ON COLUMN events.fk_device_id IS 'Foreign key reference to devices table';
COMMENT ON COLUMN events.domain IS 'Domain extracted from URL';
COMMENT ON COLUMN events.duration_seconds IS 'Time spent on the page/activity in seconds';
COMMENT ON COLUMN events.details IS 'Additional event details in JSON format';
COMMENT ON COLUMN events.is_policy_violation IS 'Flag indicating if this event violated a policy';
COMMENT ON COLUMN events.policy_rule_id IS 'ID of the policy rule that was violated';
