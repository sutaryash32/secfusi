package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity to track subscription change history.
 * Records all subscription lifecycle events: CREATE, UPGRADE, DOWNGRADE, CANCEL, RENEW, TRIAL_CONVERT.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_history")
public class SubscriptionHistory {

    public enum Action {
        CREATED,
        UPGRADED,
        DOWNGRADED,
        CANCELLED,
        RENEWED,
        TRIAL_STARTED,
        TRIAL_CONVERTED,
        TRIAL_EXPIRED,
        SUSPENDED,
        REACTIVATED,
        BILLING_CYCLE_CHANGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_history_id")
    private Long pkHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_subscription_id", nullable = false)
    private TenantSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private Action action;

    // Package change tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_from_package_id")
    private SubscriptionPackage fromPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_to_package_id")
    private SubscriptionPackage toPackage;

    // Status change tracking
    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    // Billing cycle change tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_from_billing_cycle_id")
    private BillingCycle fromBillingCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_to_billing_cycle_id")
    private BillingCycle toBillingCycle;

    // Price tracking
    @Column(name = "from_price", precision = 10, scale = 2)
    private BigDecimal fromPrice;

    @Column(name = "to_price", precision = 10, scale = 2)
    private BigDecimal toPrice;

    @Column(name = "currency", length = 3)
    private String currency;

    // Additional info
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "notes", length = 1000)
    private String notes;

    // Audit fields
    @CreationTimestamp
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", length = 100)
    private String changedBy;
}
