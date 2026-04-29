-- ============================================================
-- DATABASE SCHEMAS FOR RECENT IMPLEMENTATIONS
-- SecuFusion Tenants API
-- ============================================================

-- ============================================================
-- 1. LOGIN AUDIT EVENT TABLE
-- Purpose: Tracks all authentication-related events
-- ============================================================

CREATE TABLE login_audit_event (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50) NOT NULL,
    realm_name          VARCHAR(100),
    user_id             VARCHAR(50),
    username            VARCHAR(100),
    email               VARCHAR(150),
    event_type          VARCHAR(50) NOT NULL,
    event_timestamp     TIMESTAMP NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    client_id           VARCHAR(100),
    session_id          VARCHAR(100),
    success             BOOLEAN NOT NULL DEFAULT FALSE,
    error_message       VARCHAR(500),
    error_code          VARCHAR(50),
    auth_method         VARCHAR(50),
    mfa_used            BOOLEAN,
    remember_me         BOOLEAN,
    location            VARCHAR(200),
    device_info         VARCHAR(200),
    additional_details  TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for login_audit_event
CREATE INDEX idx_login_audit_tenant ON login_audit_event(tenant_id);
CREATE INDEX idx_login_audit_user ON login_audit_event(user_id);
CREATE INDEX idx_login_audit_username ON login_audit_event(username);
CREATE INDEX idx_login_audit_event_type ON login_audit_event(event_type);
CREATE INDEX idx_login_audit_timestamp ON login_audit_event(event_timestamp);
CREATE INDEX idx_login_audit_ip ON login_audit_event(ip_address);

-- Composite indexes for common dashboard queries
CREATE INDEX idx_login_audit_tenant_timestamp ON login_audit_event(tenant_id, event_timestamp);
CREATE INDEX idx_login_audit_tenant_type_timestamp ON login_audit_event(tenant_id, event_type, event_timestamp);

-- Event Type Enum Values:
-- LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, LOGOUT_ALL, SESSION_REVOKED,
-- TOKEN_REFRESH, TOKEN_REFRESH_FAILURE, PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS,
-- PASSWORD_CHANGE, MFA_CHALLENGE, MFA_SUCCESS, MFA_FAILURE, ACCOUNT_LOCKED,
-- ACCOUNT_UNLOCKED, ACCOUNT_DISABLED, ACCOUNT_ENABLED, IMPERSONATION_START,
-- IMPERSONATION_END, CONSENT_GRANTED, CONSENT_REVOKED, IDENTITY_PROVIDER_LOGIN,
-- IDENTITY_PROVIDER_LINK, IDENTITY_PROVIDER_UNLINK, REGISTER, REGISTER_ERROR,
-- VERIFY_EMAIL, UPDATE_EMAIL, UPDATE_PROFILE, CLIENT_LOGIN, CODE_TO_TOKEN, CODE_TO_TOKEN_ERROR


-- ============================================================
-- 2. LANDING PAGE TABLE
-- Purpose: Stores landing page configurations for tenants
-- ============================================================

CREATE TABLE landingpage (
    pk_landingpage_id   VARCHAR(36) PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(250),
    fk_tenant_id        VARCHAR(36) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for tenant lookup
CREATE INDEX idx_landingpage_tenant ON landingpage(fk_tenant_id);


-- ============================================================
-- 3. SHORTCUT TABLE
-- Purpose: Stores shortcuts/links for landing pages
-- ============================================================

CREATE TABLE shortcut (
    pk_shortcut_id      VARCHAR(36) PRIMARY KEY,
    title               VARCHAR(100) NOT NULL,
    url                 VARCHAR(500) NOT NULL,
    display_order       INTEGER DEFAULT 0,
    fk_landingpage_id   VARCHAR(36) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shortcut_landingpage
        FOREIGN KEY (fk_landingpage_id)
        REFERENCES landingpage(pk_landingpage_id)
        ON DELETE CASCADE
);

-- Index for landing page lookup
CREATE INDEX idx_shortcut_landingpage ON shortcut(fk_landingpage_id);


-- ============================================================
-- 4. TENANT TABLE (Updated with hierarchy support)
-- Purpose: Multi-tenant organization with 3-level hierarchy
-- ============================================================

CREATE TABLE tenant (
    tenantid                VARCHAR(255) PRIMARY KEY,
    tenant_name             VARCHAR(255),
    domain                  VARCHAR(255),
    email                   VARCHAR(255),
    region                  VARCHAR(255),
    phone_no                VARCHAR(255),
    tenant_type             VARCHAR(50),           -- MASTER_MSSP, MSSP, ENTERPRISE
    industry                VARCHAR(255),
    package_type            VARCHAR(255),
    billing_cycle_type      VARCHAR(255),
    features                VARCHAR(255),
    login_url               VARCHAR(255),
    status                  VARCHAR(50),           -- ACTIVE, PENDING, SUSPENDED, INACTIVE
    realm_name              VARCHAR(255),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    parent_tenant_id        VARCHAR(255),          -- For hierarchy (MSSP -> Enterprise)

    -- Foreign keys for addresses
    temporary_address_id    VARCHAR(255),
    permanent_address_id    VARCHAR(255),
    billing_address_id      VARCHAR(255),

    CONSTRAINT fk_tenant_parent
        FOREIGN KEY (parent_tenant_id)
        REFERENCES tenant(tenantid)
);

-- Indexes for tenant queries
CREATE INDEX idx_tenant_type ON tenant(tenant_type);
CREATE INDEX idx_tenant_status ON tenant(status);
CREATE INDEX idx_tenant_parent ON tenant(parent_tenant_id);
CREATE INDEX idx_tenant_type_status ON tenant(tenant_type, status);
CREATE INDEX idx_tenant_realm ON tenant(realm_name);

-- Tenant Type Values:
-- MASTER_MSSP (Level 1): Platform owner, manages all MSSPs
-- MSSP (Level 2): Managed Security Service Provider, manages Enterprises
-- ENTERPRISE (Level 3): End customer organization


-- ============================================================
-- 5. ADDRESS TABLE (Referenced by Tenant)
-- ============================================================

CREATE TABLE address (
    addressid           VARCHAR(255) PRIMARY KEY,
    line1               VARCHAR(255),
    line2               VARCHAR(255),
    city                VARCHAR(255),
    state               VARCHAR(255),
    country             VARCHAR(255),
    zip_code            VARCHAR(50)
);


-- ============================================================
-- 6. AUTH PROVIDER CONFIG TABLE
-- Purpose: Authentication provider configuration per tenant
-- ============================================================

CREATE TABLE auth_provider_config (
    auth_config_id          VARCHAR(255) PRIMARY KEY,
    provider_type           VARCHAR(100),      -- KEYCLOAK, OKTA, AZURE_AD, etc.
    client_id               VARCHAR(255),
    client_secret           VARCHAR(500),
    authorization_url       VARCHAR(500),
    token_url               VARCHAR(500),
    user_info_url           VARCHAR(500),
    jwks_url                VARCHAR(500),
    issuer_url              VARCHAR(500),
    scopes                  VARCHAR(255),
    enabled                 BOOLEAN DEFAULT TRUE,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_tenantid         VARCHAR(255),

    CONSTRAINT fk_auth_config_tenant
        FOREIGN KEY (tenant_tenantid)
        REFERENCES tenant(tenantid)
);


-- ============================================================
-- 7. USER TABLE
-- Purpose: User accounts per tenant
-- ============================================================

CREATE TABLE users (
    userid              VARCHAR(255) PRIMARY KEY,
    username            VARCHAR(255) NOT NULL,
    email               VARCHAR(255),
    first_name          VARCHAR(255),
    last_name           VARCHAR(255),
    phone_number        VARCHAR(50),
    status              VARCHAR(50),           -- ACTIVE, INACTIVE, LOCKED, PENDING
    keycloak_user_id    VARCHAR(255),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    tenant_tenantid     VARCHAR(255),

    CONSTRAINT fk_user_tenant
        FOREIGN KEY (tenant_tenantid)
        REFERENCES tenant(tenantid)
);

-- Indexes for user queries
CREATE INDEX idx_user_tenant ON users(tenant_tenantid);
CREATE INDEX idx_user_username ON users(username);
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_user_keycloak_id ON users(keycloak_user_id);


-- ============================================================
-- DASHBOARD RELATED VIEWS (Optional - for performance)
-- ============================================================

-- View: Tenant Statistics Summary
CREATE OR REPLACE VIEW v_tenant_stats AS
SELECT
    tenant_type,
    status,
    COUNT(*) as count
FROM tenant
GROUP BY tenant_type, status;

-- View: Login Statistics by Day
CREATE OR REPLACE VIEW v_daily_login_stats AS
SELECT
    tenant_id,
    DATE(event_timestamp) as login_date,
    COUNT(*) FILTER (WHERE event_type = 'LOGIN_SUCCESS') as successful_logins,
    COUNT(*) FILTER (WHERE event_type = 'LOGIN_FAILURE') as failed_logins,
    COUNT(*) as total_attempts
FROM login_audit_event
WHERE event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE')
GROUP BY tenant_id, DATE(event_timestamp);

-- View: Active Sessions per Tenant (requires Keycloak data sync)
-- Note: Active sessions are managed in Keycloak, not in database


-- ============================================================
-- SAMPLE DATA FOR TESTING
-- ============================================================

-- Insert Master MSSP (Top Level)
-- INSERT INTO tenant (tenantid, tenant_name, tenant_type, status, realm_name)
-- VALUES ('master-001', 'SecuFusion Platform', 'MASTER_MSSP', 'ACTIVE', 'master');

-- Insert MSSP (Level 2)
-- INSERT INTO tenant (tenantid, tenant_name, tenant_type, status, realm_name, parent_tenant_id)
-- VALUES ('mssp-001', 'TechSecure MSSP', 'MSSP', 'ACTIVE', 'techsecure', 'master-001');

-- Insert Enterprise (Level 3)
-- INSERT INTO tenant (tenantid, tenant_name, tenant_type, status, realm_name, parent_tenant_id)
-- VALUES ('enterprise-001', 'Acme Corp', 'ENTERPRISE', 'ACTIVE', 'acme', 'mssp-001');


-- ============================================================
-- CLEANUP / DROP STATEMENTS (Use with caution!)
-- ============================================================

-- DROP TABLE IF EXISTS shortcut CASCADE;
-- DROP TABLE IF EXISTS landingpage CASCADE;
-- DROP TABLE IF EXISTS login_audit_event CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;
-- DROP TABLE IF EXISTS auth_provider_config CASCADE;
-- DROP TABLE IF EXISTS tenant CASCADE;
-- DROP TABLE IF EXISTS address CASCADE;
-- DROP VIEW IF EXISTS v_tenant_stats;
-- DROP VIEW IF EXISTS v_daily_login_stats;
