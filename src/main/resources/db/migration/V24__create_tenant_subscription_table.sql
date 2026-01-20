-- Create tenant_subscription table for tracking tenant subscriptions with pricing and trials

CREATE TABLE IF NOT EXISTS tenant_subscription (
    pk_subscription_id BIGSERIAL PRIMARY KEY,
    fk_tenant_id VARCHAR(255) NOT NULL,
    fk_package_id BIGINT NOT NULL,
    fk_billing_cycle_id BIGINT NOT NULL,
    fk_pricing_id BIGINT,

    -- Subscription status
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    -- Subscription period
    start_date DATE NOT NULL,
    end_date DATE,

    -- Trial information
    is_trial BOOLEAN DEFAULT FALSE,
    trial_start_date DATE,
    trial_end_date DATE,
    trial_days INTEGER DEFAULT 14,
    trial_converted BOOLEAN DEFAULT FALSE,

    -- Billing information
    billing_amount DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    next_billing_date DATE,
    last_billing_date DATE,

    -- Auto-renewal
    auto_renew BOOLEAN DEFAULT TRUE,
    renewal_reminder_sent BOOLEAN DEFAULT FALSE,

    -- Grace period for expired subscriptions
    grace_period_days INTEGER DEFAULT 7,
    grace_end_date DATE,

    -- Cancellation info
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason TEXT,
    cancelled_by VARCHAR(100),

    -- Upgrade/downgrade tracking
    previous_package_id BIGINT,
    upgraded_at TIMESTAMP WITH TIME ZONE,
    downgraded_at TIMESTAMP WITH TIME ZONE,

    -- Metadata
    notes TEXT,
    custom_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_subscription_tenant FOREIGN KEY (fk_tenant_id)
        REFERENCES tenant(tenantid) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_package FOREIGN KEY (fk_package_id)
        REFERENCES package(pk_package_id),
    CONSTRAINT fk_subscription_billing_cycle FOREIGN KEY (fk_billing_cycle_id)
        REFERENCES billing_cycle(pk_billing_cycle_id),
    CONSTRAINT fk_subscription_pricing FOREIGN KEY (fk_pricing_id)
        REFERENCES package_pricing(pk_pricing_id),
    CONSTRAINT fk_subscription_prev_package FOREIGN KEY (previous_package_id)
        REFERENCES package(pk_package_id),

    -- Ensure valid status values
    CONSTRAINT chk_subscription_status CHECK (status IN ('ACTIVE', 'TRIAL', 'EXPIRED', 'CANCELLED', 'SUSPENDED', 'GRACE_PERIOD', 'PENDING'))
);

-- Unique constraint: one active subscription per tenant
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_subscription
    ON tenant_subscription(fk_tenant_id)
    WHERE status IN ('ACTIVE', 'TRIAL', 'GRACE_PERIOD');

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_subscription_tenant_id ON tenant_subscription(fk_tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_package_id ON tenant_subscription(fk_package_id);
CREATE INDEX IF NOT EXISTS idx_subscription_status ON tenant_subscription(status);
CREATE INDEX IF NOT EXISTS idx_subscription_end_date ON tenant_subscription(end_date);
CREATE INDEX IF NOT EXISTS idx_subscription_trial_end ON tenant_subscription(trial_end_date) WHERE is_trial = TRUE;
CREATE INDEX IF NOT EXISTS idx_subscription_next_billing ON tenant_subscription(next_billing_date);
CREATE INDEX IF NOT EXISTS idx_subscription_grace_end ON tenant_subscription(grace_end_date) WHERE status = 'GRACE_PERIOD';

-- Trigger to update updated_at on changes
CREATE OR REPLACE FUNCTION update_subscription_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subscription_updated_at ON tenant_subscription;
CREATE TRIGGER trg_subscription_updated_at
    BEFORE UPDATE ON tenant_subscription
    FOR EACH ROW
    EXECUTE FUNCTION update_subscription_timestamp();

COMMENT ON TABLE tenant_subscription IS 'Tenant subscription records with trial, billing, and lifecycle management';
COMMENT ON COLUMN tenant_subscription.status IS 'ACTIVE, TRIAL, EXPIRED, CANCELLED, SUSPENDED, GRACE_PERIOD, PENDING';
COMMENT ON COLUMN tenant_subscription.is_trial IS 'Whether subscription started as a trial';
COMMENT ON COLUMN tenant_subscription.trial_converted IS 'Whether trial was converted to paid subscription';
COMMENT ON COLUMN tenant_subscription.grace_period_days IS 'Days allowed after expiration before downgrade';
