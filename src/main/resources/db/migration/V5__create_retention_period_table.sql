-- Create retention_period table for defining data retention periods
CREATE TABLE IF NOT EXISTS retention_period (
    pk_retention_period_id BIGSERIAL PRIMARY KEY,
    period_name VARCHAR(50) NOT NULL UNIQUE,
    period_code VARCHAR(30) NOT NULL UNIQUE,
    period_days INTEGER,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT true
);

-- Seed retention periods
INSERT INTO retention_period (period_name, period_code, period_days, description) VALUES
('2 Days', '2_DAYS', 2, 'Data retained for 2 days'),
('2 Weeks', '2_WEEKS', 14, 'Data retained for 2 weeks'),
('30 Days', '30_DAYS', 30, 'Data retained for 30 days'),
('90 Days', '90_DAYS', 90, 'Data retained for 90 days'),
('12 Months', '12_MONTHS', 365, 'Data retained for 12 months'),
('Unlimited', 'UNLIMITED', NULL, 'Unlimited data retention');

COMMENT ON TABLE retention_period IS 'Retention period definitions for package-feature mappings';
COMMENT ON COLUMN retention_period.period_days IS 'Number of days for retention, NULL means unlimited';
