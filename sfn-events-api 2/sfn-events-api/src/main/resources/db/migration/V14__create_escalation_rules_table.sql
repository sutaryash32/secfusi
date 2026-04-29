-- V14: Escalation rules table + escalated_at on incidents

CREATE TABLE IF NOT EXISTS escalation_rules (
    pk_escalation_rule_id VARCHAR(36) PRIMARY KEY,
    fk_tenant_id          VARCHAR(255) NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    trigger_priority      VARCHAR(20),
    unassigned_minutes    INTEGER,
    unresolved_hours      INTEGER,
    escalate_to_priority  VARCHAR(20),
    notify_role           VARCHAR(100),
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(100),
    updated_by            VARCHAR(100),
    CONSTRAINT fk_er_tenant FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid),
    CONSTRAINT uk_er_tenant_name UNIQUE (fk_tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_er_tenant ON escalation_rules(fk_tenant_id);

-- Add escalated_at column to incidents for preventing re-escalation
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;
