-- V13: Webhook integration tables

CREATE TABLE IF NOT EXISTS webhook_configs (
    pk_webhook_config_id VARCHAR(36) PRIMARY KEY,
    fk_tenant_id         VARCHAR(255) NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    webhook_type         VARCHAR(20) NOT NULL,
    url                  VARCHAR(2000) NOT NULL,
    headers              JSONB,
    secret               VARCHAR(500),
    categories           JSONB,
    severities           JSONB,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    CONSTRAINT fk_wc_tenant FOREIGN KEY (fk_tenant_id) REFERENCES tenant(tenantid),
    CONSTRAINT uk_wc_tenant_name UNIQUE (fk_tenant_id, name)
);

CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    pk_delivery_log_id    VARCHAR(36) PRIMARY KEY,
    fk_webhook_config_id  VARCHAR(36) NOT NULL,
    fk_tenant_id          VARCHAR(255) NOT NULL,
    event_type            VARCHAR(100),
    status                VARCHAR(20) NOT NULL,
    http_status           INTEGER,
    attempt_count         INTEGER NOT NULL DEFAULT 1,
    request_body          TEXT,
    response_body         TEXT,
    error_message         TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wdl_config FOREIGN KEY (fk_webhook_config_id) REFERENCES webhook_configs(pk_webhook_config_id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_wc_tenant ON webhook_configs(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_wdl_config ON webhook_delivery_logs(fk_webhook_config_id);
CREATE INDEX IF NOT EXISTS idx_wdl_tenant ON webhook_delivery_logs(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_wdl_created ON webhook_delivery_logs(created_at);

-- Add webhook_enabled to notification_preferences
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS webhook_enabled BOOLEAN NOT NULL DEFAULT FALSE;
