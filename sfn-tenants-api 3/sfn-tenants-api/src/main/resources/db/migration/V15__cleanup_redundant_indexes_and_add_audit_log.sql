-- ============================================
-- V15: Cleanup redundant indexes + create audit_log table via migration
-- ============================================

-- ============================================
-- 1. Create audit_log table (previously created by Hibernate auto-DDL)
--    Making it explicit in migrations for consistency and safety.
-- ============================================
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    entity_name     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(50),
    operation       VARCHAR(10) NOT NULL,
    old_data        JSONB,
    new_data        JSONB,
    tenant_id       VARCHAR(50),
    created_by      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_name, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);

-- ============================================
-- 2. Drop redundant indexes on login_audit_event
--    These single-column indexes are covered by composite indexes.
--    Removing them reduces write overhead on every INSERT.
-- ============================================

-- idx_login_audit_tenant(tenant_id) is covered by:
--   idx_login_audit_tenant_timestamp(tenant_id, event_timestamp)
--   idx_login_audit_tenant_source(tenant_id, source_service)
--   idx_login_audit_tenant_event_type(tenant_id, event_type)
--   idx_login_audit_tenant_user(tenant_id, user_id)
DROP INDEX IF EXISTS idx_login_audit_tenant;

-- idx_login_audit_source_service(source_service) is covered by:
--   idx_login_audit_tenant_source(tenant_id, source_service)
--   Standalone source_service queries are uncommon
DROP INDEX IF EXISTS idx_login_audit_source_service;

-- ============================================
-- 3. Drop redundant index on tenant_code
--    V4 creates BOTH a UNIQUE constraint (uk_tenant_code) AND an index (idx_tenant_code).
--    PostgreSQL automatically creates an index for UNIQUE constraints,
--    so idx_tenant_code is completely redundant.
-- ============================================
DROP INDEX IF EXISTS idx_tenant_code;
