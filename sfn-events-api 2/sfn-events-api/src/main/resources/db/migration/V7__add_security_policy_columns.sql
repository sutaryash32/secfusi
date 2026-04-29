-- =====================================================
-- V7: Add security threat and policy matching columns
-- =====================================================

-- Security threat specific columns
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_security_event BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS severity VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS threat_type VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS threat_level VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS action_taken VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);

-- Policy matching columns
ALTER TABLE events ADD COLUMN IF NOT EXISTS policy_name VARCHAR(200);
ALTER TABLE events ADD COLUMN IF NOT EXISTS policy_type VARCHAR(50);
ALTER TABLE events ADD COLUMN IF NOT EXISTS filter_type VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS pattern_type VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS matched_pattern VARCHAR(500);

-- Compliance columns
ALTER TABLE events ADD COLUMN IF NOT EXISTS compliance_impact VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20) DEFAULT 'Pending';

-- Create indexes for security event queries
CREATE INDEX IF NOT EXISTS idx_events_security ON events(is_security_event);
CREATE INDEX IF NOT EXISTS idx_events_severity ON events(severity);
CREATE INDEX IF NOT EXISTS idx_events_threat_type ON events(threat_type);
CREATE INDEX IF NOT EXISTS idx_events_risk_level ON events(risk_level);
CREATE INDEX IF NOT EXISTS idx_events_processing_status ON events(processing_status);

-- Composite index for security dashboard queries
CREATE INDEX IF NOT EXISTS idx_events_security_dashboard
    ON events(fk_tenant_id, is_security_event, severity, time_stamp)
    WHERE is_security_event = TRUE;

-- Composite index for threat analysis queries
CREATE INDEX IF NOT EXISTS idx_events_threat_analysis
    ON events(fk_tenant_id, threat_type, risk_level, time_stamp)
    WHERE threat_type IS NOT NULL;

-- Composite index for policy violation analysis
CREATE INDEX IF NOT EXISTS idx_events_policy_analysis
    ON events(fk_tenant_id, policy_type, is_policy_violation, time_stamp)
    WHERE is_policy_violation = TRUE;

-- Composite index for compliance reporting
CREATE INDEX IF NOT EXISTS idx_events_compliance
    ON events(fk_tenant_id, compliance_impact, processing_status, time_stamp);

-- Add comments for new columns
COMMENT ON COLUMN events.is_security_event IS 'Flag indicating if this is a security-related event';
COMMENT ON COLUMN events.severity IS 'Severity level: critical, high, medium, low';
COMMENT ON COLUMN events.threat_type IS 'Type of threat: csp_violation, malware, phishing, xss, etc.';
COMMENT ON COLUMN events.threat_level IS 'Threat level classification';
COMMENT ON COLUMN events.action_taken IS 'Action taken: blocked, allowed, warned';
COMMENT ON COLUMN events.risk_level IS 'Risk assessment: Critical, High, Medium, Low';
COMMENT ON COLUMN events.policy_name IS 'Name of the policy that was triggered';
COMMENT ON COLUMN events.policy_type IS 'Type of policy: BROWSER_POLICY, NETWORK_POLICY, DLP';
COMMENT ON COLUMN events.filter_type IS 'URL filter type: WHITELIST, BLACKLIST';
COMMENT ON COLUMN events.pattern_type IS 'Pattern matching type: DOMAIN, REGEX, URL';
COMMENT ON COLUMN events.matched_pattern IS 'The pattern that matched the violation';
COMMENT ON COLUMN events.compliance_impact IS 'Impact on compliance: High, Medium, Low';
COMMENT ON COLUMN events.processing_status IS 'Processing status: Pending, Processed, Reviewed';
