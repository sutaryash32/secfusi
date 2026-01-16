-- Seed the complete package-feature matrix
-- This script maps all features to all packages with appropriate access levels and retention periods

-- Helper function to insert mapping
-- Package: Trial, Freemium, Basic, Standard, Premium
-- Access Levels: NO (0), COMING_SOON (1), LIMITED (2), BASIC (3), YES (4), ADVANCED (5), ADVANCED_CUSTOM (6), ADVANCED_SCHEDULED (7)
-- Retention Periods: 2_DAYS, 2_WEEKS, 30_DAYS, 90_DAYS, 12_MONTHS, UNLIMITED

-- =====================================================
-- BROWSER CONFIGURATION & CONTROL
-- =====================================================

-- Homepage / startup control: Trial=YES, Free=YES, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_HOMEPAGE_CONTROL' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Force browser sign-in: Trial=NO, Free=YES, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial' AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_FORCE_SIGNIN' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Freemium', 'Basic', 'Standard', 'Premium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_FORCE_SIGNIN' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Disable incognito mode: Trial=NO, Free=YES, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial' AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_INCOGNITO' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Freemium', 'Basic', 'Standard', 'Premium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_INCOGNITO' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Disable password saving: Trial=NO, Free=NO, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_PASSWORD_SAVE' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_PASSWORD_SAVE' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Disable developer tools: Trial=NO, Free=NO, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_DEVTOOLS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_DISABLE_DEVTOOLS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Lock browser settings: Trial=NO, Free=NO, Basic=YES, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_LOCK_SETTINGS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium') AND fg.group_code = 'BROWSER_CONFIG' AND f.feature_code = 'BROWSER_LOCK_SETTINGS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- EXTENSION MANAGEMENT
-- =====================================================

-- Extension inventory: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'EXTENSION_MGMT' AND f.feature_code = 'EXT_INVENTORY' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Allow/block extensions: Trial=NO, Free=NO, Basic=NO, Standard=YES, Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic') AND fg.group_code = 'EXTENSION_MGMT' AND f.feature_code = 'EXT_ALLOW_BLOCK' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium') AND fg.group_code = 'EXTENSION_MGMT' AND f.feature_code = 'EXT_ALLOW_BLOCK' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Approved extension list, Force-install, Lock settings, Control permissions: Standard+Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'EXTENSION_MGMT'
AND f.feature_code IN ('EXT_APPROVED_LIST', 'EXT_FORCE_INSTALL', 'EXT_LOCK_SETTINGS', 'EXT_CONTROL_PERMS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'EXTENSION_MGMT'
AND f.feature_code IN ('EXT_APPROVED_LIST', 'EXT_FORCE_INSTALL', 'EXT_LOCK_SETTINGS', 'EXT_CONTROL_PERMS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Extension risk scoring, Conditional policies, Auto-remediation: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard')
AND fg.group_code = 'EXTENSION_MGMT'
AND f.feature_code IN ('EXT_RISK_SCORING', 'EXT_CONDITIONAL_POLICIES', 'EXT_AUTO_REMEDIATION')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'EXTENSION_MGMT'
AND f.feature_code IN ('EXT_RISK_SCORING', 'EXT_CONDITIONAL_POLICIES', 'EXT_AUTO_REMEDIATION')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- DATA PROTECTION (DLP)
-- =====================================================

-- PII detection: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'DATA_PROTECTION' AND f.feature_code = 'DLP_PII_DETECTION' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Warn user: Trial=NO, Free=YES, Basic+Standard+Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial' AND fg.group_code = 'DATA_PROTECTION' AND f.feature_code = 'DLP_WARN_USER' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Freemium', 'Basic', 'Standard', 'Premium') AND fg.group_code = 'DATA_PROTECTION' AND f.feature_code = 'DLP_WARN_USER' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Block uploads/downloads, Domain allowlist, AD group policies: Basic+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'DATA_PROTECTION'
AND f.feature_code IN ('DLP_BLOCK_UPLOADS', 'DLP_BLOCK_DOWNLOADS', 'DLP_DOMAIN_ALLOWLIST', 'DLP_AD_GROUP_POLICIES')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'DATA_PROTECTION'
AND f.feature_code IN ('DLP_BLOCK_UPLOADS', 'DLP_BLOCK_DOWNLOADS', 'DLP_DOMAIN_ALLOWLIST', 'DLP_AD_GROUP_POLICIES')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Clipboard controls: Standard+Premium
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic') AND fg.group_code = 'DATA_PROTECTION' AND f.feature_code = 'DLP_CLIPBOARD_CONTROLS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium') AND fg.group_code = 'DATA_PROTECTION' AND f.feature_code = 'DLP_CLIPBOARD_CONTROLS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Context-aware DLP, Adaptive enforcement, AI inspection: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard')
AND fg.group_code = 'DATA_PROTECTION'
AND f.feature_code IN ('DLP_CONTEXT_AWARE', 'DLP_ADAPTIVE_ENFORCEMENT', 'DLP_AI_INSPECTION')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'DATA_PROTECTION'
AND f.feature_code IN ('DLP_CONTEXT_AWARE', 'DLP_ADAPTIVE_ENFORCEMENT', 'DLP_AI_INSPECTION')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- POLICY & ADMINISTRATION
-- =====================================================

-- Central policy management, Global settings: Free+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial'
AND fg.group_code = 'POLICY_ADMIN'
AND f.feature_code IN ('POLICY_CENTRAL_MGMT', 'POLICY_GLOBAL_SETTINGS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'POLICY_ADMIN'
AND f.feature_code IN ('POLICY_CENTRAL_MGMT', 'POLICY_GLOBAL_SETTINGS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Policy exceptions, Audit logs: Basic+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'POLICY_ADMIN'
AND f.feature_code IN ('POLICY_EXCEPTIONS', 'POLICY_AUDIT_LOGS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'POLICY_ADMIN'
AND f.feature_code IN ('POLICY_EXCEPTIONS', 'POLICY_AUDIT_LOGS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Role-based admin access: Standard+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic') AND fg.group_code = 'POLICY_ADMIN' AND f.feature_code = 'POLICY_RBAC' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium') AND fg.group_code = 'POLICY_ADMIN' AND f.feature_code = 'POLICY_RBAC' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Advanced audit & compliance: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard') AND fg.group_code = 'POLICY_ADMIN' AND f.feature_code = 'POLICY_ADVANCED_AUDIT' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium' AND fg.group_code = 'POLICY_ADMIN' AND f.feature_code = 'POLICY_ADVANCED_AUDIT' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- REPORTING & INSIGHTS
-- =====================================================

-- Standard reports: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'REPORTING' AND f.feature_code = 'REPORT_STANDARD' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Export reports, Usage analytics: Basic+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'REPORTING'
AND f.feature_code IN ('REPORT_EXPORT', 'REPORT_USAGE_ANALYTICS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'REPORTING'
AND f.feature_code IN ('REPORT_EXPORT', 'REPORT_USAGE_ANALYTICS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Custom reports, Risk & compliance: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard')
AND fg.group_code = 'REPORTING'
AND f.feature_code IN ('REPORT_CUSTOM', 'REPORT_RISK_COMPLIANCE')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'REPORTING'
AND f.feature_code IN ('REPORT_CUSTOM', 'REPORT_RISK_COMPLIANCE')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- ZERO TRUST & ACCESS CONTROL
-- =====================================================

-- Identity-based access: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_IDENTITY_ACCESS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- IDP Configuration: Free+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial' AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_IDP_CONFIG' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Freemium', 'Basic', 'Standard', 'Premium') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_IDP_CONFIG' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Domain allowlist/blocklist: Basic+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_DOMAIN_ACCESS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_DOMAIN_ACCESS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Browser Access Control: Standard+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_BROWSER_ACCESS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_BROWSER_ACCESS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Context-aware access: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard') AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_CONTEXT_ACCESS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium' AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_CONTEXT_ACCESS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- AI OPERATIONS
-- =====================================================

-- AI Usage Monitoring, LLM Access Controls: Standard+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'AI_OPS'
AND f.feature_code IN ('AI_USAGE_MONITORING', 'AI_LLM_CONTROLS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'AI_OPS'
AND f.feature_code IN ('AI_USAGE_MONITORING', 'AI_LLM_CONTROLS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Prompt Management, AI Agent Controls, AI Workspace: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard')
AND fg.group_code = 'AI_OPS'
AND f.feature_code IN ('AI_PROMPT_MGMT', 'AI_AGENT_CONTROLS', 'AI_WORKSPACE_MGMT')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'AI_OPS'
AND f.feature_code IN ('AI_PROMPT_MGMT', 'AI_AGENT_CONTROLS', 'AI_WORKSPACE_MGMT')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- SECURITY OPERATIONS (SecOps)
-- =====================================================

-- Alerting & notifications: Basic+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium') AND fg.group_code = 'SECOPS' AND f.feature_code = 'SECOPS_ALERTING' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium') AND fg.group_code = 'SECOPS' AND f.feature_code = 'SECOPS_ALERTING' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Security Event Monitoring, AI Event Monitoring: Standard+
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'SECOPS'
AND f.feature_code IN ('SECOPS_SECURITY_EVENTS', 'SECOPS_AI_EVENTS')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'SECOPS'
AND f.feature_code IN ('SECOPS_SECURITY_EVENTS', 'SECOPS_AI_EVENTS')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Incident management, MITRE ATT&CK mapping: Premium only
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard')
AND fg.group_code = 'SECOPS'
AND f.feature_code IN ('SECOPS_INCIDENT_MGMT', 'SECOPS_MITRE_MAPPING')
AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'SECOPS'
AND f.feature_code IN ('SECOPS_INCIDENT_MGMT', 'SECOPS_MITRE_MAPPING')
AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;
