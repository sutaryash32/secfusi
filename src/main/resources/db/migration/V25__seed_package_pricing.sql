-- Seed package pricing for all packages and billing cycles
-- Pricing strategy: Annual gets 20% off, Biennial gets 30% off

-- Helper function to insert pricing if not exists
DO $$
DECLARE
    v_trial_pkg_id BIGINT;
    v_freemium_pkg_id BIGINT;
    v_basic_pkg_id BIGINT;
    v_standard_pkg_id BIGINT;
    v_premium_pkg_id BIGINT;
    v_monthly_cycle_id BIGINT;
    v_quarterly_cycle_id BIGINT;
    v_semi_annual_cycle_id BIGINT;
    v_annual_cycle_id BIGINT;
    v_biennial_cycle_id BIGINT;
    v_trial_cycle_id BIGINT;
BEGIN
    -- Get package IDs
    SELECT pk_package_id INTO v_trial_pkg_id FROM package WHERE package_name = 'Trial';
    SELECT pk_package_id INTO v_freemium_pkg_id FROM package WHERE package_name = 'Freemium';
    SELECT pk_package_id INTO v_basic_pkg_id FROM package WHERE package_name = 'Basic';
    SELECT pk_package_id INTO v_standard_pkg_id FROM package WHERE package_name = 'Standard';
    SELECT pk_package_id INTO v_premium_pkg_id FROM package WHERE package_name = 'Premium';

    -- Get billing cycle IDs
    SELECT pk_billing_cycle_id INTO v_monthly_cycle_id FROM billing_cycle WHERE cycle_code = 'MONTHLY';
    SELECT pk_billing_cycle_id INTO v_quarterly_cycle_id FROM billing_cycle WHERE cycle_code = 'QUARTERLY';
    SELECT pk_billing_cycle_id INTO v_semi_annual_cycle_id FROM billing_cycle WHERE cycle_code = 'SEMI_ANNUAL';
    SELECT pk_billing_cycle_id INTO v_annual_cycle_id FROM billing_cycle WHERE cycle_code = 'ANNUAL';
    SELECT pk_billing_cycle_id INTO v_biennial_cycle_id FROM billing_cycle WHERE cycle_code = 'BIENNIAL';
    SELECT pk_billing_cycle_id INTO v_trial_cycle_id FROM billing_cycle WHERE cycle_code = 'TRIAL';

    -- TRIAL package pricing (free, only trial billing cycle)
    IF v_trial_pkg_id IS NOT NULL AND v_trial_cycle_id IS NOT NULL THEN
        INSERT INTO package_pricing (fk_package_id, fk_billing_cycle_id, base_price, currency, discount_percentage, price_per_user, min_users, max_users)
        VALUES (v_trial_pkg_id, v_trial_cycle_id, 0.00, 'USD', 0, 0.00, 1, 5)
        ON CONFLICT (fk_package_id, fk_billing_cycle_id) DO NOTHING;
    END IF;

    -- FREEMIUM package pricing (free, only monthly)
    IF v_freemium_pkg_id IS NOT NULL AND v_monthly_cycle_id IS NOT NULL THEN
        INSERT INTO package_pricing (fk_package_id, fk_billing_cycle_id, base_price, currency, discount_percentage, price_per_user, min_users, max_users)
        VALUES (v_freemium_pkg_id, v_monthly_cycle_id, 0.00, 'USD', 0, 0.00, 1, 3)
        ON CONFLICT (fk_package_id, fk_billing_cycle_id) DO NOTHING;
    END IF;

    -- BASIC package pricing
    -- $29/month, $79/quarter (10% off), $139/semi-annual (20% off), $249/annual (28% off), $449/biennial (35% off)
    IF v_basic_pkg_id IS NOT NULL THEN
        INSERT INTO package_pricing (fk_package_id, fk_billing_cycle_id, base_price, currency, discount_percentage, price_per_user, min_users, max_users)
        VALUES
            (v_basic_pkg_id, v_monthly_cycle_id, 29.00, 'USD', 0, 5.00, 1, 10),
            (v_basic_pkg_id, v_quarterly_cycle_id, 87.00, 'USD', 10, 4.50, 1, 10),
            (v_basic_pkg_id, v_semi_annual_cycle_id, 174.00, 'USD', 20, 4.00, 1, 10),
            (v_basic_pkg_id, v_annual_cycle_id, 348.00, 'USD', 28, 3.50, 1, 10),
            (v_basic_pkg_id, v_biennial_cycle_id, 696.00, 'USD', 35, 3.00, 1, 10)
        ON CONFLICT (fk_package_id, fk_billing_cycle_id) DO NOTHING;
    END IF;

    -- STANDARD package pricing
    -- $79/month, $215/quarter (10% off), $379/semi-annual (20% off), $679/annual (28% off), $1199/biennial (35% off)
    IF v_standard_pkg_id IS NOT NULL THEN
        INSERT INTO package_pricing (fk_package_id, fk_billing_cycle_id, base_price, currency, discount_percentage, price_per_user, min_users, max_users)
        VALUES
            (v_standard_pkg_id, v_monthly_cycle_id, 79.00, 'USD', 0, 8.00, 1, 50),
            (v_standard_pkg_id, v_quarterly_cycle_id, 237.00, 'USD', 10, 7.20, 1, 50),
            (v_standard_pkg_id, v_semi_annual_cycle_id, 474.00, 'USD', 20, 6.40, 1, 50),
            (v_standard_pkg_id, v_annual_cycle_id, 948.00, 'USD', 28, 5.60, 1, 50),
            (v_standard_pkg_id, v_biennial_cycle_id, 1896.00, 'USD', 35, 4.80, 1, 50)
        ON CONFLICT (fk_package_id, fk_billing_cycle_id) DO NOTHING;
    END IF;

    -- PREMIUM package pricing
    -- $199/month, $539/quarter (10% off), $959/semi-annual (20% off), $1719/annual (28% off), $3039/biennial (35% off)
    IF v_premium_pkg_id IS NOT NULL THEN
        INSERT INTO package_pricing (fk_package_id, fk_billing_cycle_id, base_price, currency, discount_percentage, price_per_user, min_users, max_users)
        VALUES
            (v_premium_pkg_id, v_monthly_cycle_id, 199.00, 'USD', 0, 15.00, 1, NULL),
            (v_premium_pkg_id, v_quarterly_cycle_id, 597.00, 'USD', 10, 13.50, 1, NULL),
            (v_premium_pkg_id, v_semi_annual_cycle_id, 1194.00, 'USD', 20, 12.00, 1, NULL),
            (v_premium_pkg_id, v_annual_cycle_id, 2388.00, 'USD', 28, 10.50, 1, NULL),
            (v_premium_pkg_id, v_biennial_cycle_id, 4776.00, 'USD', 35, 9.00, 1, NULL)
        ON CONFLICT (fk_package_id, fk_billing_cycle_id) DO NOTHING;
    END IF;

END $$;

-- Add trial_days column to package table if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'package' AND column_name = 'trial_days'
    ) THEN
        ALTER TABLE package ADD COLUMN trial_days INTEGER DEFAULT 14;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'package' AND column_name = 'is_trial_available'
    ) THEN
        ALTER TABLE package ADD COLUMN is_trial_available BOOLEAN DEFAULT TRUE;
    END IF;
END $$;

-- Update packages with trial availability
UPDATE package SET trial_days = 0, is_trial_available = FALSE WHERE package_name = 'Trial';
UPDATE package SET trial_days = 0, is_trial_available = FALSE WHERE package_name = 'Freemium';
UPDATE package SET trial_days = 14, is_trial_available = TRUE WHERE package_name = 'Basic';
UPDATE package SET trial_days = 14, is_trial_available = TRUE WHERE package_name = 'Standard';
UPDATE package SET trial_days = 30, is_trial_available = TRUE WHERE package_name = 'Premium';

COMMENT ON COLUMN package.trial_days IS 'Number of days for trial period (0 = no trial)';
COMMENT ON COLUMN package.is_trial_available IS 'Whether trial is available for this package';
