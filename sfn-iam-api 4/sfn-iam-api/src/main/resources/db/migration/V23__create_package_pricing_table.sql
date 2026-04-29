-- Create package_pricing table for per-billing-cycle pricing
-- This allows different prices for same package based on billing cycle (monthly vs annual, etc.)

CREATE TABLE IF NOT EXISTS package_pricing (
    pk_pricing_id BIGSERIAL PRIMARY KEY,
    fk_package_id BIGINT NOT NULL,
    fk_billing_cycle_id BIGINT NOT NULL,
    base_price DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    discount_percentage DECIMAL(5, 2) DEFAULT 0,
    final_price DECIMAL(10, 2),
    price_per_user DECIMAL(10, 2),
    min_users INTEGER DEFAULT 1,
    max_users INTEGER,
    setup_fee DECIMAL(10, 2) DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    valid_from DATE,
    valid_until DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_pricing_package FOREIGN KEY (fk_package_id)
        REFERENCES package(pk_package_id) ON DELETE CASCADE,
    CONSTRAINT fk_pricing_billing_cycle FOREIGN KEY (fk_billing_cycle_id)
        REFERENCES billing_cycle(pk_billing_cycle_id)
);

-- Add unique constraint to prevent duplicate pricing for same package-billing cycle
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_package_billing_pricing'
    ) THEN
        ALTER TABLE package_pricing
            ADD CONSTRAINT uk_package_billing_pricing UNIQUE (fk_package_id, fk_billing_cycle_id);
    END IF;
END $$;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_pricing_package_id ON package_pricing(fk_package_id);
CREATE INDEX IF NOT EXISTS idx_pricing_billing_cycle_id ON package_pricing(fk_billing_cycle_id);
CREATE INDEX IF NOT EXISTS idx_pricing_is_active ON package_pricing(is_active);
CREATE INDEX IF NOT EXISTS idx_pricing_currency ON package_pricing(currency);

-- Add computed final_price trigger
CREATE OR REPLACE FUNCTION calculate_final_price()
RETURNS TRIGGER AS $$
BEGIN
    NEW.final_price = NEW.base_price * (1 - COALESCE(NEW.discount_percentage, 0) / 100);
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_calculate_final_price ON package_pricing;
CREATE TRIGGER trg_calculate_final_price
    BEFORE INSERT OR UPDATE OF base_price, discount_percentage ON package_pricing
    FOR EACH ROW
    EXECUTE FUNCTION calculate_final_price();

COMMENT ON TABLE package_pricing IS 'Package pricing per billing cycle with discounts and user-based pricing';
COMMENT ON COLUMN package_pricing.base_price IS 'Base price before any discounts';
COMMENT ON COLUMN package_pricing.discount_percentage IS 'Discount percentage (e.g., 20 for 20% off annual)';
COMMENT ON COLUMN package_pricing.final_price IS 'Calculated final price after discount';
COMMENT ON COLUMN package_pricing.price_per_user IS 'Additional price per user beyond min_users';
