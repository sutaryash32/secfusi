-- ============================================
-- V14: Add performance indexes to tenant table
-- These indexes support the most frequently queried columns
-- to eliminate full table scans as tenant count grows.
-- ============================================

-- tenant_name: used in findByTenantName, existsByTenantName, findByTenantNameWithUsers
-- Already has logical uniqueness; this enforces it at DB level too
CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_name ON tenant(tenant_name);

-- domain: used in findByDomain, existsByDomain, normalization checks
CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_domain ON tenant(domain);

-- parentTenantId: used in findByParentTenantId, findByParentTenantIdIn (hierarchy queries)
CREATE INDEX IF NOT EXISTS idx_tenant_parent ON tenant(parent_tenant_id);

-- status: used in countByTenantTypeAndStatus, findByParentTenantIdAndStatus
CREATE INDEX IF NOT EXISTS idx_tenant_status ON tenant(status);

-- email: used in existsByEmail, existsTenantByEmailDomain, existsByEmailSubdomain
CREATE INDEX IF NOT EXISTS idx_tenant_email ON tenant(email);

-- realmName: used in findByRealmName (SSO repair operations)
CREATE INDEX IF NOT EXISTS idx_tenant_realm ON tenant(realm_name);
