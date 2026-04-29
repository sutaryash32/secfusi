-- =====================================================
-- V10: Incident Management tables
-- =====================================================
-- This migration adds:
-- 1. incidents table for grouping security events into trackable cases
-- 2. incident_events junction table for many-to-many event linking
-- 3. incident_activities table for timeline/audit trail
-- 4. Sequence for generating human-readable incident numbers
-- =====================================================

-- =====================================================
-- Part 1: Create incidents table
-- =====================================================

CREATE TABLE IF NOT EXISTS incidents (
    pk_incident_id      VARCHAR(36) PRIMARY KEY,
    fk_tenant_id        VARCHAR(255) NOT NULL,

    -- Incident identification
    incident_number     VARCHAR(20) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    description         TEXT,

    -- Classification
    status              VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority            VARCHAR(20) NOT NULL DEFAULT 'P3_MEDIUM',
    category            VARCHAR(50),

    -- Assignment
    assigned_to         VARCHAR(36),
    assigned_to_name    VARCHAR(200),
    assigned_at         TIMESTAMPTZ,

    -- Resolution
    resolved_by         VARCHAR(36),
    resolved_by_name    VARCHAR(200),
    resolved_at         TIMESTAMPTZ,
    resolution_notes    TEXT,
    root_cause          VARCHAR(50),

    -- Closure
    closed_at           TIMESTAMPTZ,

    -- Metrics
    event_count         INTEGER NOT NULL DEFAULT 0,

    -- Source tracking
    source              VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    auto_rule_name      VARCHAR(200),

    -- Audit columns
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_incident_tenant FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED', 'FALSE_POSITIVE')),
    CONSTRAINT chk_incident_priority CHECK (priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW')),
    CONSTRAINT chk_incident_source CHECK (source IN ('MANUAL', 'AUTO')),
    CONSTRAINT uk_incident_number_tenant UNIQUE (fk_tenant_id, incident_number)
);

-- Indexes for incidents
CREATE INDEX IF NOT EXISTS idx_incident_tenant ON incidents(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_incident_status ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incident_priority ON incidents(priority);
CREATE INDEX IF NOT EXISTS idx_incident_assigned_to ON incidents(assigned_to);
CREATE INDEX IF NOT EXISTS idx_incident_category ON incidents(category);
CREATE INDEX IF NOT EXISTS idx_incident_created_at ON incidents(fk_tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_incident_resolved_at ON incidents(resolved_at);

-- Composite index for dashboard queries
CREATE INDEX IF NOT EXISTS idx_incident_dashboard
    ON incidents(fk_tenant_id, status, priority, created_at DESC);

-- Comments
COMMENT ON TABLE incidents IS 'Security incidents grouping multiple events into trackable cases';
COMMENT ON COLUMN incidents.incident_number IS 'Human-readable incident number, e.g., INC-000001';
COMMENT ON COLUMN incidents.status IS 'Lifecycle: OPEN, INVESTIGATING, RESOLVED, CLOSED, FALSE_POSITIVE';
COMMENT ON COLUMN incidents.priority IS 'P1_CRITICAL, P2_HIGH, P3_MEDIUM, P4_LOW';
COMMENT ON COLUMN incidents.source IS 'MANUAL (portal user) or AUTO (auto-creation rule)';
COMMENT ON COLUMN incidents.assigned_to IS 'Portal user ID of the assigned analyst';
COMMENT ON COLUMN incidents.resolved_by IS 'Portal user ID of the person who resolved the incident';
COMMENT ON COLUMN incidents.root_cause IS 'Root cause category after investigation';

-- =====================================================
-- Part 2: Create incident_events junction table
-- =====================================================

CREATE TABLE IF NOT EXISTS incident_events (
    pk_incident_event_id    VARCHAR(36) PRIMARY KEY,
    fk_incident_id          VARCHAR(36) NOT NULL,
    fk_event_id             VARCHAR(255) NOT NULL,
    fk_tenant_id            VARCHAR(255) NOT NULL,

    linked_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    linked_by               VARCHAR(100),

    -- Audit columns
    created_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_ie_incident FOREIGN KEY (fk_incident_id) REFERENCES incidents(pk_incident_id) ON DELETE CASCADE,
    CONSTRAINT fk_ie_event FOREIGN KEY (fk_event_id) REFERENCES events(pk_event_id),
    CONSTRAINT uk_incident_event UNIQUE (fk_incident_id, fk_event_id)
);

-- Indexes for incident_events
CREATE INDEX IF NOT EXISTS idx_ie_incident ON incident_events(fk_incident_id);
CREATE INDEX IF NOT EXISTS idx_ie_event ON incident_events(fk_event_id);
CREATE INDEX IF NOT EXISTS idx_ie_tenant ON incident_events(fk_tenant_id);

-- Comments
COMMENT ON TABLE incident_events IS 'Junction table linking incidents to security events (many-to-many)';
COMMENT ON COLUMN incident_events.linked_by IS 'Username who linked the event to the incident';

-- =====================================================
-- Part 3: Create incident_activities table (timeline)
-- =====================================================

CREATE TABLE IF NOT EXISTS incident_activities (
    pk_incident_activity_id     VARCHAR(36) PRIMARY KEY,
    fk_incident_id              VARCHAR(36) NOT NULL,
    fk_tenant_id                VARCHAR(255) NOT NULL,

    -- Activity details
    action                      VARCHAR(50) NOT NULL,
    description                 TEXT NOT NULL,

    -- Who performed the action
    performed_by                VARCHAR(36),
    performed_by_name           VARCHAR(200),
    performed_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Change tracking
    old_value                   VARCHAR(500),
    new_value                   VARCHAR(500),

    -- Audit columns
    created_at                  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_ia_incident FOREIGN KEY (fk_incident_id) REFERENCES incidents(pk_incident_id) ON DELETE CASCADE
);

-- Indexes for incident_activities
CREATE INDEX IF NOT EXISTS idx_ia_incident ON incident_activities(fk_incident_id);
CREATE INDEX IF NOT EXISTS idx_ia_tenant ON incident_activities(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_ia_performed_at ON incident_activities(fk_incident_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_ia_action ON incident_activities(action);

-- Comments
COMMENT ON TABLE incident_activities IS 'Audit trail / timeline for incident lifecycle changes';
COMMENT ON COLUMN incident_activities.action IS 'CREATED, STATUS_CHANGED, PRIORITY_CHANGED, ASSIGNED, EVENT_LINKED, COMMENT_ADDED, RESOLVED, CLOSED, etc.';

-- =====================================================
-- Part 4: Sequence for incident numbers
-- =====================================================

CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1 INCREMENT BY 1;

COMMENT ON SEQUENCE incident_number_seq IS 'Generates sequential incident numbers for INC-XXXXXX format';
