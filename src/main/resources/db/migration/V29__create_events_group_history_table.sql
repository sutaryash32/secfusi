CREATE TABLE IF NOT EXISTS events_group_history (
    pk_history_id           VARCHAR(36)     NOT NULL PRIMARY KEY,
    fk_tenant_id            VARCHAR(36)     NOT NULL,
    fk_events_group_id      VARCHAR(36)     NOT NULL,
    group_name              VARCHAR(255),
    action                  VARCHAR(50)     NOT NULL,
    details                 VARCHAR(1000),
    policy_assignment_id    VARCHAR(36),
    policy_type             VARCHAR(20),
    policy_name             VARCHAR(255),
    performed_by            VARCHAR(100)    NOT NULL,
    performed_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_egh_tenant    ON events_group_history (fk_tenant_id);
CREATE INDEX idx_egh_group     ON events_group_history (fk_events_group_id);
CREATE INDEX idx_egh_action    ON events_group_history (action);
CREATE INDEX idx_egh_timestamp ON events_group_history (performed_at);
