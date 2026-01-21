-- =====================================================
-- V26: Create Addon Pricing Table
-- =====================================================
-- Purpose: Store pricing for addon features per billing cycle
-- Features marked as addons can be purchased separately by tenants
-- =====================================================

-- Add is_addon flag to features table
ALTER TABLE features ADD COLUMN IF NOT EXISTS is_addon BOOLEAN DEFAULT FALSE;
ALTER TABLE features ADD COLUMN IF NOT EXISTS addon_monthly_price DECIMAL(10,2);
ALTER TABLE features ADD COLUMN IF NOT EXISTS addon_trial_days INTEGER DEFAULT 7;

-- Create addon_pricing table for per-billing-cycle pricing
CREATE TABLE IF NOT EXISTS addon_pricing (
    pk_addon_pricing_id BIGSERIAL PRIMARY KEY,

    -- Feature reference
    fk_feature_id BIGINT NOT NULL REFERENCES features(pk_feature_id),

    -- Billing cycle reference
    fk_billing_cycle_id BIGINT NOT NULL REFERENCES billing_cycle(pk_billing_cycle_id),

    -- Pricing
    price DECIMAL(10,2) NOT NULL,
    discount_percentage DECIMAL(5,2) DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD',

    -- Status
    is_active BOOLEAN DEFAULT TRUE,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    -- Ensure unique pricing per feature per billing cycle
    CONSTRAINT uk_addon_feature_billing_cycle UNIQUE (fk_feature_id, fk_billing_cycle_id)
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_addon_pricing_feature ON addon_pricing(fk_feature_id);
CREATE INDEX IF NOT EXISTS idx_addon_pricing_billing_cycle ON addon_pricing(fk_billing_cycle_id);
CREATE INDEX IF NOT EXISTS idx_addon_pricing_active ON addon_pricing(is_active) WHERE is_active = TRUE;

-- Add trigger for updated_at
CREATE OR REPLACE FUNCTION update_addon_pricing_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_addon_pricing_updated_at ON addon_pricing;
CREATE TRIGGER trigger_addon_pricing_updated_at
    BEFORE UPDATE ON addon_pricing
    FOR EACH ROW
    EXECUTE FUNCTION update_addon_pricing_timestamp();

COMMENT ON TABLE addon_pricing IS 'Pricing for addon features per billing cycle';
COMMENT ON COLUMN addon_pricing.price IS 'Price for this addon with this billing cycle';
COMMENT ON COLUMN addon_pricing.discount_percentage IS 'Discount from monthly rate for longer billing cycles';
COMMENT ON COLUMN features.is_addon IS 'Whether this feature can be purchased as an addon';
COMMENT ON COLUMN features.addon_monthly_price IS 'Base monthly price when purchased as addon';
COMMENT ON COLUMN features.addon_trial_days IS 'Trial days available for addon feature';
