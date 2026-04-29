-- =====================================================
-- V27: Seed Addon Features and Pricing
-- =====================================================
-- Purpose: Mark certain features as purchasable addons
-- and set up pricing for each billing cycle
-- =====================================================

-- =====================================================
-- 1. Mark Advanced Features as Addons
-- =====================================================
-- These are premium features that can be purchased
-- separately to enhance any package

-- AI Operations Addons (Advanced AI features)
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 49.99,
    addon_trial_days = 7
WHERE feature_code IN ('AI_PROMPT_MGMT', 'AI_AGENT_CONTROLS', 'AI_WORKSPACE_MGMT');

-- Advanced DLP Addons
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 29.99,
    addon_trial_days = 7
WHERE feature_code IN ('DLP_CONTEXT_AWARE', 'DLP_ADAPTIVE_ENFORCEMENT', 'DLP_AI_INSPECTION');

-- Advanced Extension Management Addons
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 19.99,
    addon_trial_days = 7
WHERE feature_code IN ('EXT_RISK_SCORING', 'EXT_CONDITIONAL_POLICIES', 'EXT_AUTO_REMEDIATION');

-- Advanced Security Operations Addons
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 39.99,
    addon_trial_days = 14
WHERE feature_code IN ('SECOPS_INCIDENT_MGMT', 'SECOPS_MITRE_MAPPING');

-- Advanced Reporting Addons
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 24.99,
    addon_trial_days = 7
WHERE feature_code IN ('REPORT_CUSTOM', 'REPORT_RISK_COMPLIANCE');

-- Advanced Zero Trust Addons
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 34.99,
    addon_trial_days = 7
WHERE feature_code = 'ZT_CONTEXT_ACCESS';

-- Advanced Audit Addon
UPDATE features SET
    is_addon = true,
    addon_monthly_price = 19.99,
    addon_trial_days = 7
WHERE feature_code = 'POLICY_ADVANCED_AUDIT';

-- =====================================================
-- 2. Create Addon Pricing for Each Billing Cycle
-- =====================================================
-- Pricing follows same discount structure as packages:
-- Monthly: Base price
-- Quarterly: 10% off
-- Semi-Annual: 20% off
-- Annual: 28% off
-- Biennial: 35% off

-- Helper function to insert addon pricing
DO $$
DECLARE
    feature_rec RECORD;
    billing_rec RECORD;
    calc_price DECIMAL(10,2);
    discount DECIMAL(5,2);
BEGIN
    -- Loop through all addon features
    FOR feature_rec IN
        SELECT pk_feature_id, feature_code, addon_monthly_price
        FROM features
        WHERE is_addon = true AND addon_monthly_price IS NOT NULL
    LOOP
        -- Loop through active billing cycles
        FOR billing_rec IN
            SELECT pk_billing_cycle_id, cycle_code, duration_months
            FROM billing_cycles
            WHERE is_active = true AND cycle_code != 'TRIAL'
        LOOP
            -- Calculate discount based on billing cycle
            discount := CASE billing_rec.cycle_code
                WHEN 'MONTHLY' THEN 0
                WHEN 'QUARTERLY' THEN 10
                WHEN 'SEMI_ANNUAL' THEN 20
                WHEN 'ANNUAL' THEN 28
                WHEN 'BIENNIAL' THEN 35
                ELSE 0
            END;

            -- Calculate price: (monthly_price * months) * (1 - discount/100)
            calc_price := (feature_rec.addon_monthly_price * billing_rec.duration_months) * (1 - discount/100);

            -- Insert pricing record
            INSERT INTO addon_pricing (
                fk_feature_id,
                fk_billing_cycle_id,
                price,
                discount_percentage,
                currency,
                is_active,
                created_by
            ) VALUES (
                feature_rec.pk_feature_id,
                billing_rec.pk_billing_cycle_id,
                ROUND(calc_price, 2),
                discount,
                'USD',
                true,
                'system'
            )
            ON CONFLICT (fk_feature_id, fk_billing_cycle_id) DO UPDATE SET
                price = EXCLUDED.price,
                discount_percentage = EXCLUDED.discount_percentage,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = 'system';
        END LOOP;
    END LOOP;
END $$;

-- =====================================================
-- 3. Create Addon Bundles (Future Enhancement)
-- =====================================================
-- These are commented out for future implementation
-- Bundles would allow purchasing multiple addons at a discount

-- CREATE TABLE IF NOT EXISTS addon_bundles (
--     pk_bundle_id BIGSERIAL PRIMARY KEY,
--     bundle_code VARCHAR(50) UNIQUE NOT NULL,
--     bundle_name VARCHAR(100) NOT NULL,
--     description TEXT,
--     bundle_discount_percentage DECIMAL(5,2) DEFAULT 15,
--     is_active BOOLEAN DEFAULT TRUE
-- );

-- CREATE TABLE IF NOT EXISTS addon_bundle_features (
--     pk_bundle_feature_id BIGSERIAL PRIMARY KEY,
--     fk_bundle_id BIGINT REFERENCES addon_bundles(pk_bundle_id),
--     fk_feature_id BIGINT REFERENCES features(pk_feature_id),
--     UNIQUE(fk_bundle_id, fk_feature_id)
-- );

-- Sample bundles:
-- 'AI_SECURITY_BUNDLE': AI_PROMPT_MGMT, AI_AGENT_CONTROLS, DLP_AI_INSPECTION
-- 'ADVANCED_DLP_BUNDLE': DLP_CONTEXT_AWARE, DLP_ADAPTIVE_ENFORCEMENT, DLP_AI_INSPECTION
-- 'ENTERPRISE_SECURITY_BUNDLE': All advanced addons

-- =====================================================
-- Verification Queries (for testing)
-- =====================================================
-- SELECT f.feature_code, f.addon_monthly_price,
--        bc.cycle_code, ap.price, ap.discount_percentage
-- FROM addon_pricing ap
-- JOIN features f ON ap.fk_feature_id = f.pk_feature_id
-- JOIN billing_cycles bc ON ap.fk_billing_cycle_id = bc.pk_billing_cycle_id
-- ORDER BY f.feature_code, bc.duration_months;
