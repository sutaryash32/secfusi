-- V13: Playbook system tables

CREATE TABLE IF NOT EXISTS playbook_templates (
    pk_playbook_template_id VARCHAR(36) PRIMARY KEY,
    fk_tenant_id            VARCHAR(255) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT,
    category                VARCHAR(50),
    steps                   JSONB NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    CONSTRAINT fk_pt_tenant FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid),
    CONSTRAINT uk_pt_tenant_name UNIQUE (fk_tenant_id, name)
);

CREATE TABLE IF NOT EXISTS incident_playbooks (
    pk_incident_playbook_id VARCHAR(36) PRIMARY KEY,
    fk_incident_id          VARCHAR(36) NOT NULL,
    fk_playbook_template_id VARCHAR(36) NOT NULL,
    fk_tenant_id            VARCHAR(255) NOT NULL,
    total_steps             INTEGER NOT NULL,
    completed_steps         INTEGER NOT NULL DEFAULT 0,
    is_complete             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100),
    updated_by              VARCHAR(100),
    CONSTRAINT fk_ip_incident FOREIGN KEY (fk_incident_id) REFERENCES incidents(pk_incident_id),
    CONSTRAINT fk_ip_template FOREIGN KEY (fk_playbook_template_id) REFERENCES playbook_templates(pk_playbook_template_id),
    CONSTRAINT uk_ip_incident_template UNIQUE (fk_incident_id, fk_playbook_template_id)
);

CREATE TABLE IF NOT EXISTS incident_playbook_steps (
    pk_incident_playbook_step_id VARCHAR(36) PRIMARY KEY,
    fk_incident_playbook_id      VARCHAR(36) NOT NULL,
    fk_tenant_id                 VARCHAR(255) NOT NULL,
    step_number                  INTEGER NOT NULL,
    title                        VARCHAR(300) NOT NULL,
    description                  TEXT,
    is_required                  BOOLEAN NOT NULL DEFAULT FALSE,
    is_completed                 BOOLEAN NOT NULL DEFAULT FALSE,
    completed_by                 VARCHAR(36),
    completed_by_name            VARCHAR(200),
    completed_at                 TIMESTAMPTZ,
    notes                        TEXT,
    created_at                   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by                   VARCHAR(100),
    updated_by                   VARCHAR(100),
    CONSTRAINT fk_ips_playbook FOREIGN KEY (fk_incident_playbook_id)
        REFERENCES incident_playbooks(pk_incident_playbook_id) ON DELETE CASCADE,
    CONSTRAINT uk_ips_playbook_step UNIQUE (fk_incident_playbook_id, step_number)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_pt_tenant ON playbook_templates(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_pt_category ON playbook_templates(fk_tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_ip_incident ON incident_playbooks(fk_incident_id);
CREATE INDEX IF NOT EXISTS idx_ip_tenant ON incident_playbooks(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_ips_playbook ON incident_playbook_steps(fk_incident_playbook_id);
CREATE INDEX IF NOT EXISTS idx_ips_tenant ON incident_playbook_steps(fk_tenant_id);
