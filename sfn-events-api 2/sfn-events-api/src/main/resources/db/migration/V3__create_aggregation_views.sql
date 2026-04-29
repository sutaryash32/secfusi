-- =====================================================
-- V3: Create views for common aggregation queries
-- =====================================================

-- View: Device statistics per tenant
CREATE OR REPLACE VIEW v_device_stats AS
SELECT
    tenant_id,
    COUNT(*) AS total_devices,
    COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_devices,
    COUNT(*) FILTER (WHERE status = 'INACTIVE') AS inactive_devices,
    COUNT(*) FILTER (WHERE status = 'BLOCKED') AS blocked_devices
FROM devices
GROUP BY tenant_id;

-- View: Device distribution by type per tenant
CREATE OR REPLACE VIEW v_device_by_type AS
SELECT
    tenant_id,
    device_type,
    COUNT(*) AS device_count
FROM devices
GROUP BY tenant_id, device_type;

-- View: Daily event counts per tenant
CREATE OR REPLACE VIEW v_daily_event_counts AS
SELECT
    fk_tenant_id AS tenant_id,
    DATE(time_stamp) AS event_date,
    event_type,
    COUNT(*) AS event_count
FROM events
WHERE time_stamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY fk_tenant_id, DATE(time_stamp), event_type;

-- View: Top domains per tenant (last 7 days)
CREATE OR REPLACE VIEW v_top_domains AS
SELECT
    fk_tenant_id AS tenant_id,
    domain,
    COUNT(*) AS visit_count
FROM events
WHERE event_type = 'WEBSITE_VISIT'
  AND domain IS NOT NULL
  AND time_stamp >= CURRENT_DATE - INTERVAL '7 days'
GROUP BY fk_tenant_id, domain
ORDER BY visit_count DESC;

-- View: Active users per tenant (last 7 days)
CREATE OR REPLACE VIEW v_active_users AS
SELECT
    fk_tenant_id AS tenant_id,
    user_name,
    COUNT(*) AS event_count,
    MAX(time_stamp) AS last_activity_at
FROM events
WHERE time_stamp >= CURRENT_DATE - INTERVAL '7 days'
  AND user_name IS NOT NULL
GROUP BY fk_tenant_id, user_name;

-- View: Policy violations summary
CREATE OR REPLACE VIEW v_policy_violations AS
SELECT
    fk_tenant_id AS tenant_id,
    DATE(time_stamp) AS violation_date,
    event_type,
    policy_rule_id,
    COUNT(*) AS violation_count
FROM events
WHERE is_policy_violation = TRUE
  AND time_stamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY fk_tenant_id, DATE(time_stamp), event_type, policy_rule_id;

-- View: Device activity summary
CREATE OR REPLACE VIEW v_device_activity AS
SELECT
    d.device_id,
    d.device_name,
    d.tenant_id,
    d.user_name,
    d.status,
    d.first_seen_at,
    d.last_seen_at,
    COUNT(e.pk_event_id) AS total_events,
    COUNT(e.pk_event_id) FILTER (WHERE e.time_stamp >= CURRENT_DATE) AS events_today,
    COUNT(e.pk_event_id) FILTER (WHERE e.time_stamp >= CURRENT_DATE - INTERVAL '7 days') AS events_this_week,
    COALESCE(SUM(e.duration_seconds), 0) AS total_duration_seconds
FROM devices d
LEFT JOIN events e ON d.device_id = e.fk_device_id
GROUP BY d.device_id, d.device_name, d.tenant_id, d.user_name, d.status, d.first_seen_at, d.last_seen_at;

-- Add comments
COMMENT ON VIEW v_device_stats IS 'Aggregated device statistics per tenant';
COMMENT ON VIEW v_device_by_type IS 'Device distribution by type per tenant';
COMMENT ON VIEW v_daily_event_counts IS 'Daily event counts by type for the last 30 days';
COMMENT ON VIEW v_top_domains IS 'Most visited domains per tenant for the last 7 days';
COMMENT ON VIEW v_active_users IS 'Active users and their event counts for the last 7 days';
COMMENT ON VIEW v_policy_violations IS 'Policy violation summary for the last 30 days';
COMMENT ON VIEW v_device_activity IS 'Device activity summary with event counts';
