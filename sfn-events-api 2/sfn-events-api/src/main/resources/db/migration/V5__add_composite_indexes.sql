-- =====================================================
-- V5: Add composite indexes for optimized queries
-- =====================================================

-- Composite index for device inactivity check
CREATE INDEX IF NOT EXISTS idx_device_tenant_status_lastseen
    ON devices(tenant_id, status, last_seen_at);

-- Composite index for tenant event queries with time range
CREATE INDEX IF NOT EXISTS idx_events_tenant_timestamp
    ON events(fk_tenant_id, time_stamp DESC);

-- Composite index for device event queries with time range
CREATE INDEX IF NOT EXISTS idx_events_device_timestamp
    ON events(fk_device_id, time_stamp DESC);

-- Composite index for user event queries
CREATE INDEX IF NOT EXISTS idx_events_tenant_user_timestamp
    ON events(fk_tenant_id, user_name, time_stamp DESC);

-- Composite index for policy violation queries
CREATE INDEX IF NOT EXISTS idx_events_tenant_violation_timestamp
    ON events(fk_tenant_id, is_policy_violation, time_stamp DESC)
    WHERE is_policy_violation = TRUE;

-- Composite index for domain analytics
CREATE INDEX IF NOT EXISTS idx_events_tenant_domain_timestamp
    ON events(fk_tenant_id, domain, time_stamp DESC)
    WHERE domain IS NOT NULL;

-- Composite index for event type analytics
CREATE INDEX IF NOT EXISTS idx_events_tenant_type_timestamp
    ON events(fk_tenant_id, event_type, time_stamp DESC);

-- Add comments
COMMENT ON INDEX idx_device_tenant_status_lastseen IS 'Optimizes device inactivity scheduler queries';
COMMENT ON INDEX idx_events_tenant_timestamp IS 'Optimizes tenant-wide event queries with time filtering';
COMMENT ON INDEX idx_events_device_timestamp IS 'Optimizes per-device event history queries';
COMMENT ON INDEX idx_events_tenant_user_timestamp IS 'Optimizes per-user event queries';
COMMENT ON INDEX idx_events_tenant_violation_timestamp IS 'Optimizes policy violation queries';
COMMENT ON INDEX idx_events_tenant_domain_timestamp IS 'Optimizes domain analytics queries';
COMMENT ON INDEX idx_events_tenant_type_timestamp IS 'Optimizes event type distribution queries';
