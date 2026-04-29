-- V12: Create notification_preferences table
-- Per user+tenant channel preferences by notification category

CREATE TABLE notification_preferences (
    pk_preference_id    VARCHAR(36)     NOT NULL DEFAULT gen_random_uuid()::text,
    fk_tenant_id        VARCHAR(36)     NOT NULL,
    fk_user_id          VARCHAR(36)     NOT NULL,

    category            VARCHAR(50),

    email_enabled       BOOLEAN         NOT NULL DEFAULT TRUE,
    websocket_enabled   BOOLEAN         NOT NULL DEFAULT TRUE,
    in_app_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,

    min_severity        VARCHAR(20),

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification_preferences PRIMARY KEY (pk_preference_id),
    CONSTRAINT fk_notif_prefs_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES tenant(tenantid) ON DELETE CASCADE,
    CONSTRAINT uq_notif_pref_user_cat UNIQUE (fk_tenant_id, fk_user_id, category)
);

CREATE INDEX idx_notif_prefs_tenant_user ON notification_preferences(fk_tenant_id, fk_user_id);
