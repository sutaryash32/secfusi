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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "package_pricing",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_package_billing_pricing",
                columnNames = {"fk_package_id", "fk_billing_cycle_id"}
        ))
public class PackagePricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_pricing_id")
    private Long pkPricingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_package_id", nullable = false)
    private SubscriptionPackage pkg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "USD";

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    @Column(name = "final_price", precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "price_per_user", precision = 10, scale = 2)
    private BigDecimal pricePerUser;

    @Column(name = "min_users")
    private Integer minUsers = 1;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "setup_fee", precision = 10, scale = 2)
    private BigDecimal setupFee = BigDecimal.ZERO;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

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
