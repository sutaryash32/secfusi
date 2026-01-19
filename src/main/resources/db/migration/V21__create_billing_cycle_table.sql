-- Create billing_cycle table for subscription billing periods

CREATE TABLE IF NOT EXISTS billing_cycle (
    pk_billing_cycle_id BIGSERIAL PRIMARY KEY,
    cycle_name VARCHAR(100) NOT NULL,
    cycle_code VARCHAR(50) NOT NULL,
    duration_months INTEGER,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE
);

-- Add unique constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_billing_cycle_code'
    ) THEN
        ALTER TABLE billing_cycle ADD CONSTRAINT uk_billing_cycle_code UNIQUE (cycle_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_billing_cycle_name'
    ) THEN
        ALTER TABLE billing_cycle ADD CONSTRAINT uk_billing_cycle_name UNIQUE (cycle_name);
    END IF;
END $$;

-- Seed billing cycle data
INSERT INTO billing_cycle (cycle_name, cycle_code, duration_months, description, is_active)
VALUES
    ('Monthly', 'MONTHLY', 1, 'Billed every month', TRUE),
    ('Quarterly', 'QUARTERLY', 3, 'Billed every 3 months', TRUE),
    ('Semi-Annual', 'SEMI_ANNUAL', 6, 'Billed every 6 months', TRUE),
    ('Annual', 'ANNUAL', 12, 'Billed every 12 months', TRUE),
    ('Biennial', 'BIENNIAL', 24, 'Billed every 2 years', TRUE),
    ('Trial', 'TRIAL', 0, 'Trial period - no billing', TRUE)
ON CONFLICT (cycle_code) DO NOTHING;

-- Add billing_cycle reference to package table if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'package' AND column_name = 'fk_billing_cycle_id'
    ) THEN
        ALTER TABLE package ADD COLUMN fk_billing_cycle_id BIGINT;
        ALTER TABLE package ADD CONSTRAINT fk_package_billing_cycle
            FOREIGN KEY (fk_billing_cycle_id) REFERENCES billing_cycle(pk_billing_cycle_id);
    END IF;
END $$;

-- Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_billing_cycle_is_active ON billing_cycle(is_active);
