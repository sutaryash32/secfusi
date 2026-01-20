package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pricing for addon features per billing cycle.
 * Allows different prices for monthly, quarterly, annual addon subscriptions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "addon_pricing",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_addon_feature_billing_cycle",
                columnNames = {"fk_feature_id", "fk_billing_cycle_id"}
        ))
public class AddonPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_addon_pricing_id")
    private Long pkAddonPricingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_feature_id", nullable = false)
    private Feature feature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "is_active")
    private Boolean isActive = true;

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
}
