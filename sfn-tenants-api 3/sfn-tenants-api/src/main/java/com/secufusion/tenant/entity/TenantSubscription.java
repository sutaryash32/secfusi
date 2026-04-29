package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_subscription")
public class TenantSubscription {

    public enum Status {
        ACTIVE,
        TRIAL,
        EXPIRED,
        CANCELLED,
        SUSPENDED,
        GRACE_PERIOD,
        PENDING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_subscription_id")
    private Long pkSubscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_package_id", nullable = false)
    private SubscriptionPackage pkg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_pricing_id")
    private PackagePricing pricing;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Trial information
    @Column(name = "is_trial")
    private Boolean isTrial = false;

    @Column(name = "trial_start_date")
    private LocalDate trialStartDate;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    @Column(name = "trial_days")
    private Integer trialDays = 14;

    @Column(name = "trial_converted")
    private Boolean trialConverted = false;

    // Billing information
    @Column(name = "billing_amount", precision = 10, scale = 2)
    private BigDecimal billingAmount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "last_billing_date")
    private LocalDate lastBillingDate;

    // Auto-renewal
    @Column(name = "auto_renew")
    private Boolean autoRenew = true;

    @Column(name = "renewal_reminder_sent")
    private Boolean renewalReminderSent = false;

    // Grace period
    @Column(name = "grace_period_days")
    private Integer gracePeriodDays = 7;

    @Column(name = "grace_end_date")
    private LocalDate graceEndDate;

    // Cancellation info
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    // Upgrade/downgrade tracking
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_package_id")
    private SubscriptionPackage previousPackage;

    @Column(name = "upgraded_at")
    private Instant upgradedAt;

    @Column(name = "downgraded_at")
    private Instant downgradedAt;

    // Metadata
    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Check if subscription is currently active (includes ACTIVE, TRIAL, GRACE_PERIOD).
     */
    public boolean isCurrentlyActive() {
        return status == Status.ACTIVE || status == Status.TRIAL || status == Status.GRACE_PERIOD;
    }

    /**
     * Check if trial has expired.
     */
    public boolean isTrialExpired() {
        return isTrial != null && isTrial && trialEndDate != null && LocalDate.now().isAfter(trialEndDate);
    }

    /**
     * Check if subscription is in grace period.
     */
    public boolean isInGracePeriod() {
        return status == Status.GRACE_PERIOD && graceEndDate != null && !LocalDate.now().isAfter(graceEndDate);
    }

    /**
     * Calculate days remaining in trial.
     */
    public long getTrialDaysRemaining() {
        if (trialEndDate == null || !Boolean.TRUE.equals(isTrial)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), trialEndDate);
        return Math.max(0, days);
    }

    /**
     * Calculate days until next billing.
     */
    public long getDaysUntilNextBilling() {
        if (nextBillingDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextBillingDate);
    }
}
