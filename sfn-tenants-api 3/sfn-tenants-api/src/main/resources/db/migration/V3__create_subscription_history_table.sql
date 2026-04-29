-- ============================================
-- V3: Create subscription_history table
-- Tracks all subscription changes for tenants
-- ============================================

CREATE TABLE subscription_history (
    pk_history_id BIGSERIAL PRIMARY KEY,
    fk_subscription_id BIGINT REFERENCES tenant_subscription(pk_subscription_id),
    fk_tenant_id VARCHAR(255) NOT NULL REFERENCES tenant(tenantid),
    action VARCHAR(30) NOT NULL,
    fk_from_package_id BIGINT REFERENCES package(pk_package_id),
    fk_to_package_id BIGINT REFERENCES package(pk_package_id),
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    fk_from_billing_cycle_id BIGINT REFERENCES billing_cycle(pk_billing_cycle_id),
    fk_to_billing_cycle_id BIGINT REFERENCES billing_cycle(pk_billing_cycle_id),
    from_price DECIMAL(10,2),
    to_price DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'USD',
    reason TEXT,
    notes TEXT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(100)
);

-- Indexes for better query performance
CREATE INDEX idx_subscription_history_tenant ON subscription_history(fk_tenant_id);
CREATE INDEX idx_subscription_history_subscription ON subscription_history(fk_subscription_id);
CREATE INDEX idx_subscription_history_action ON subscription_history(action);
CREATE INDEX idx_subscription_history_changed_at ON subscription_history(changed_at DESC);

-- Composite index for common queries
CREATE INDEX idx_subscription_history_tenant_action ON subscription_history(fk_tenant_id, action);

-- ============================================
-- Valid action values (enum in Java):
-- CREATED, UPGRADED, DOWNGRADED, CANCELLED,
-- RENEWED, TRIAL_STARTED, TRIAL_CONVERTED,
-- TRIAL_EXPIRED, SUSPENDED, REACTIVATED,
-- BILLING_CYCLE_CHANGED
-- ============================================

COMMENT ON TABLE subscription_history IS 'Tracks all subscription lifecycle changes for audit and reporting';
COMMENT ON COLUMN subscription_history.action IS 'Type of subscription change: CREATED, UPGRADED, DOWNGRADED, CANCELLED, RENEWED, TRIAL_STARTED, TRIAL_CONVERTED, TRIAL_EXPIRED, SUSPENDED, REACTIVATED, BILLING_CYCLE_CHANGED';
COMMENT ON COLUMN subscription_history.from_status IS 'Previous subscription status before change';
COMMENT ON COLUMN subscription_history.to_status IS 'New subscription status after change';
