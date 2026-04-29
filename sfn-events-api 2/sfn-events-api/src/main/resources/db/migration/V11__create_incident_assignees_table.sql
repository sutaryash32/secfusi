-- =====================================================
-- V11: Incident Assignee Configuration table
-- =====================================================
-- Admin-configurable mapping of analysts to incident categories.
-- Used by auto-create to perform least-loaded assignment.
-- category=NULL means "handles ALL categories" (fallback/wildcard).
-- =====================================================

CREATE TABLE IF NOT EXISTS incident_assignees (
    pk_incident_assignee_id     VARCHAR(36) PRIMARY KEY,
    fk_tenant_id                VARCHAR(255) NOT NULL,

    -- Category scope (NULL = handles all categories as fallback)
    category                    VARCHAR(50),

    -- Analyst identity (portal user from sfn-iam-api)
    user_id                     VARCHAR(36) NOT NULL,
    user_name                   VARCHAR(200) NOT NULL,

    -- Config
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    assignment_order            INTEGER NOT NULL DEFAULT 0,

    -- Audit columns
    created_at                  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),

    -- Constraints
    CONSTRAINT fk_ia_tenant FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid),
    CONSTRAINT uk_assignee_tenant_category_user UNIQUE (fk_tenant_id, category, user_id)
);

-- Partial unique index: prevent duplicate fallback entries (category IS NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uk_assignee_fallback_user
    ON incident_assignees(fk_tenant_id, user_id) WHERE category IS NULL;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_ia_tenant ON incident_assignees(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_ia_category ON incident_assignees(fk_tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_ia_user ON incident_assignees(fk_tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ia_active ON incident_assignees(fk_tenant_id, is_active);

-- Comments
COMMENT ON TABLE incident_assignees IS 'Configuration table mapping analysts to incident categories for auto-assignment';
COMMENT ON COLUMN incident_assignees.category IS 'Incident category this analyst handles. NULL means all categories (fallback)';
COMMENT ON COLUMN incident_assignees.assignment_order IS 'Tiebreaker when analysts have equal load. Lower number = higher priority';
COMMENT ON COLUMN incident_assignees.is_active IS 'Whether this assignment config is active. Inactive entries are skipped during auto-assign';
