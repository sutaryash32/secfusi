-- V11: Create notifications table for in-app notification storage
-- Each row represents a notification delivered to a specific user

CREATE TABLE notifications (
    pk_notification_id  VARCHAR(36)     NOT NULL DEFAULT gen_random_uuid()::text,
    fk_tenant_id        VARCHAR(36)     NOT NULL,
    fk_user_id          VARCHAR(36)     NOT NULL,

    type                VARCHAR(50)     NOT NULL,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    title               VARCHAR(500)    NOT NULL,
    message             TEXT            NOT NULL,

    source_service      VARCHAR(50)     NOT NULL,
    source_entity_id    VARCHAR(36),
    source_entity_type  VARCHAR(50),

    channels            VARCHAR(100),

    is_read             BOOLEAN         NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,

    metadata            JSONB,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notifications PRIMARY KEY (pk_notification_id),
    CONSTRAINT fk_notifications_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES tenant(tenantid) ON DELETE CASCADE
);

CREATE INDEX idx_notifications_tenant_user ON notifications(fk_tenant_id, fk_user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(fk_user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
