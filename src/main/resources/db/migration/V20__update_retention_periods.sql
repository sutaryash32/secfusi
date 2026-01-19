-- Update existing package-feature mappings with proper retention periods
-- Based on the feature matrix specifications

-- =====================================================
-- SECOPS - Security Events with Retention
-- =====================================================

-- Trial: Security Events = Limited (2 Weeks)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '2_WEEKS'
AND al.level_code = 'LIMITED'
AND p.package_name = 'Trial'
AND f.feature_code = 'SECOPS_SECURITY_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Freemium: Security Events = Limited (2 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '2_DAYS'
AND al.level_code = 'LIMITED'
AND p.package_name = 'Freemium'
AND f.feature_code = 'SECOPS_SECURITY_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Basic/Standard: Security Events = Yes (30 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '30_DAYS'
AND al.level_code = 'YES'
AND p.package_name IN ('Basic', 'Standard')
AND f.feature_code = 'SECOPS_SECURITY_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Premium: Security Events = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND al.level_code = 'YES'
AND p.package_name = 'Premium'
AND f.feature_code = 'SECOPS_SECURITY_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- SECOPS - AI Events with Retention
-- =====================================================

-- Basic/Standard: AI Events = Yes (30 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '30_DAYS'
AND p.package_name IN ('Basic', 'Standard')
AND f.feature_code = 'SECOPS_AI_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Premium: AI Events = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND p.package_name = 'Premium'
AND f.feature_code = 'SECOPS_AI_EVENTS'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- SECOPS - Alerting with Retention
-- =====================================================

-- Basic/Standard: Alerting = Yes (30 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '30_DAYS'
AND p.package_name IN ('Basic', 'Standard')
AND f.feature_code = 'SECOPS_ALERTING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Premium: Alerting = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND p.package_name = 'Premium'
AND f.feature_code = 'SECOPS_ALERTING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- SECOPS - Incidents with Retention
-- =====================================================

-- Basic/Standard/Premium: Incidents = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND p.package_name IN ('Basic', 'Standard', 'Premium')
AND f.feature_code = 'SECOPS_INCIDENT_MGMT'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- SECOPS - MITRE TTPs with Retention
-- =====================================================

-- Standard/Premium: MITRE TTPs = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND p.package_name IN ('Standard', 'Premium')
AND f.feature_code = 'SECOPS_MITRE_MAPPING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- AI OPS - AI Usage Monitoring with Retention
-- =====================================================

-- Trial: AI Usage Monitoring = Limited (2 Weeks)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '2_WEEKS'
AND al.level_code = 'LIMITED'
AND p.package_name = 'Trial'
AND f.feature_code = 'AI_USAGE_MONITORING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Freemium: AI Usage Monitoring = Limited (2 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id,
    fk_access_level_id = al.pk_access_level_id
FROM retention_period rp, access_level al, package p, features f
WHERE rp.period_code = '2_DAYS'
AND al.level_code = 'LIMITED'
AND p.package_name = 'Freemium'
AND f.feature_code = 'AI_USAGE_MONITORING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Basic/Standard: AI Usage Monitoring = Yes (30 Days)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '30_DAYS'
AND p.package_name IN ('Basic', 'Standard')
AND f.feature_code = 'AI_USAGE_MONITORING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Premium: AI Usage Monitoring = Yes (12 Months)
UPDATE package_feature_mapping pfm
SET fk_retention_period_id = rp.pk_retention_period_id
FROM retention_period rp, package p, features f
WHERE rp.period_code = '12_MONTHS'
AND p.package_name = 'Premium'
AND f.feature_code = 'AI_USAGE_MONITORING'
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- =====================================================
-- DATA PROTECTION - DLP Access Levels
-- =====================================================

-- Trial: DLP features (Block uploads, downloads, etc.) = YES (was incorrectly YES, matrix shows YES for Trial)
-- Actually per first matrix, Trial has PII detection=YES, Warn=NO, Block=NO
-- Keep as is since V12 already set this correctly

-- Freemium: No DLP (already set in V12)

-- Basic: DLP = BASIC
UPDATE package_feature_mapping pfm
SET fk_access_level_id = al.pk_access_level_id
FROM access_level al, package p, features f
WHERE al.level_code = 'BASIC'
AND p.package_name = 'Basic'
AND f.feature_code IN ('DLP_BLOCK_UPLOADS', 'DLP_BLOCK_DOWNLOADS', 'DLP_DOMAIN_ALLOWLIST', 'DLP_AD_GROUP_POLICIES')
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;

-- Standard/Premium: DLP = ADVANCED
UPDATE package_feature_mapping pfm
SET fk_access_level_id = al.pk_access_level_id
FROM access_level al, package p, features f
WHERE al.level_code = 'ADVANCED'
AND p.package_name IN ('Standard', 'Premium')
AND f.feature_code IN ('DLP_BLOCK_UPLOADS', 'DLP_BLOCK_DOWNLOADS', 'DLP_DOMAIN_ALLOWLIST', 'DLP_AD_GROUP_POLICIES', 'DLP_CLIPBOARD_CONTROLS')
AND pfm.fk_package_id = p.pk_package_id
AND pfm.fk_feature_id = f.pk_feature_id;
