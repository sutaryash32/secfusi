-- Add missing package-feature mappings for newly added features
-- Also includes proper retention periods as per the feature matrix

-- =====================================================
-- CONFIGURATION
-- =====================================================

-- Browser Configuration: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'CONFIGURATION' AND f.feature_code = 'CONFIG_BROWSER' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Extension Configuration: All=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'CONFIGURATION' AND f.feature_code = 'CONFIG_EXTENSION' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- POLICY PROFILE
-- =====================================================

-- Unified Policy: Trial/Free/Basic=YES, Standard/Premium=ADVANCED
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'POLICY_PROFILE' AND f.feature_code = 'POLICY_UNIFIED' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Standard'
AND fg.group_code = 'POLICY_PROFILE' AND f.feature_code = 'POLICY_UNIFIED' AND al.level_code = 'ADVANCED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'POLICY_PROFILE' AND f.feature_code = 'POLICY_UNIFIED' AND al.level_code = 'ADVANCED_CUSTOM'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- USERS & DEVICES
-- =====================================================

-- Users: Trial/Free=LIMITED, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_MGMT' AND al.level_code = 'LIMITED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_MGMT' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Groups/OUs: Trial/Free=LIMITED, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_GROUPS_OUS' AND al.level_code = 'LIMITED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_GROUPS_OUS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Devices: Trial/Free/Basic=NO, Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_DEVICES_MGMT' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'USERS_DEVICES' AND f.feature_code = 'USERS_DEVICES_MGMT' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- INSIGHTS & ANALYTICS (with retention periods)
-- =====================================================

-- Analytics: Trial/Free=BASIC, Basic=YES, Standard/Premium=ADVANCED
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_ANALYTICS' AND al.level_code = 'BASIC'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Basic'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_ANALYTICS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_ANALYTICS' AND al.level_code = 'ADVANCED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- User Activity: Trial=YES, Free=LIMITED(2Days), Basic/Standard=YES(30Days), Premium=YES(12Months)
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_USER_ACTIVITY' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name = 'Freemium'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_USER_ACTIVITY' AND al.level_code = 'LIMITED' AND rp.period_code = '2_DAYS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name IN ('Basic', 'Standard')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_USER_ACTIVITY' AND al.level_code = 'YES' AND rp.period_code = '30_DAYS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name = 'Premium'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_USER_ACTIVITY' AND al.level_code = 'YES' AND rp.period_code = '12_MONTHS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Browser Activity: Trial=YES, Free=LIMITED(2Days), Basic/Standard=YES(30Days), Premium=YES(12Months)
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Trial'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_BROWSER_ACTIVITY' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name = 'Freemium'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_BROWSER_ACTIVITY' AND al.level_code = 'LIMITED' AND rp.period_code = '2_DAYS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name IN ('Basic', 'Standard')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_BROWSER_ACTIVITY' AND al.level_code = 'YES' AND rp.period_code = '30_DAYS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name = 'Premium'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_BROWSER_ACTIVITY' AND al.level_code = 'YES' AND rp.period_code = '12_MONTHS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- AI Usage (Insights): Trial/Free=NO, Basic/Standard=YES(30Days), Premium=YES(12Months)
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_AI_USAGE' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name IN ('Basic', 'Standard')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_AI_USAGE' AND al.level_code = 'YES' AND rp.period_code = '30_DAYS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, rp.pk_retention_period_id, true
FROM package p, feature_group fg, features f, access_level al, retention_period rp
WHERE p.package_name = 'Premium'
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_AI_USAGE' AND al.level_code = 'YES' AND rp.period_code = '12_MONTHS'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Compliance: Trial/Free/Basic=NO, Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_COMPLIANCE' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'INSIGHTS' AND f.feature_code = 'INSIGHTS_COMPLIANCE' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- ZERO TRUST - Additional Features
-- =====================================================

-- Network: Trial/Free/Basic=NO, Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_NETWORK' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_NETWORK' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Access Control: Trial/Free=NO, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_ACCESS_CONTROL' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_ACCESS_CONTROL' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- URL Filtering: Trial/Free=NO, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_URL_FILTERING' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_URL_FILTERING' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- IdP Integration: Trial/Free/Basic=NO, Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_IDP_INTEGRATION' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'ZERO_TRUST' AND f.feature_code = 'ZT_IDP_INTEGRATION' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- AI OPERATIONS - Additional Features
-- =====================================================

-- AI Guardrails: Trial/Free=NO, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_GUARDRAILS' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_GUARDRAILS' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Prompt Manager: Trial/Free=NO, Basic/Standard/Premium=YES
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_PROMPT_MANAGER' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Basic', 'Standard', 'Premium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_PROMPT_MANAGER' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Agent Controls: All=COMING_SOON
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_AGENT_CONTROLS' AND al.level_code = 'COMING_SOON'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- Workspace: All=COMING_SOON
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium', 'Basic', 'Standard', 'Premium')
AND fg.group_code = 'AI_OPS' AND f.feature_code = 'AI_WORKSPACE_MGMT' AND al.level_code = 'COMING_SOON'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- GLOBAL SETTINGS
-- =====================================================

-- Global Settings: Trial/Free=LIMITED, Basic=YES, Standard/Premium=ADVANCED
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'GLOBAL_SETTINGS' AND f.feature_code = 'GLOBAL_SETTINGS_MAIN' AND al.level_code = 'LIMITED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Basic'
AND fg.group_code = 'GLOBAL_SETTINGS' AND f.feature_code = 'GLOBAL_SETTINGS_MAIN' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Standard', 'Premium')
AND fg.group_code = 'GLOBAL_SETTINGS' AND f.feature_code = 'GLOBAL_SETTINGS_MAIN' AND al.level_code = 'ADVANCED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

-- =====================================================
-- REPORTS (Main Reports Feature)
-- =====================================================

-- Reports: Trial/Free=NO, Basic=YES, Standard=ADVANCED, Premium=ADVANCED_SCHEDULED
INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name IN ('Trial', 'Freemium')
AND fg.group_code = 'REPORTING' AND f.feature_code = 'REPORT_MAIN' AND al.level_code = 'NO'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Basic'
AND fg.group_code = 'REPORTING' AND f.feature_code = 'REPORT_MAIN' AND al.level_code = 'YES'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Standard'
AND fg.group_code = 'REPORTING' AND f.feature_code = 'REPORT_MAIN' AND al.level_code = 'ADVANCED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;

INSERT INTO package_feature_mapping (fk_package_id, fk_feature_group_id, fk_feature_id, fk_access_level_id, fk_retention_period_id, is_enabled)
SELECT p.pk_package_id, fg.pk_feature_group_id, f.pk_feature_id, al.pk_access_level_id, NULL, true
FROM package p, feature_group fg, features f, access_level al
WHERE p.package_name = 'Premium'
AND fg.group_code = 'REPORTING' AND f.feature_code = 'REPORT_MAIN' AND al.level_code = 'ADVANCED_SCHEDULED'
ON CONFLICT (fk_package_id, fk_feature_id) DO NOTHING;
