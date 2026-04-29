package com.secufusion.tenant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing subscription packages.
 * Maps to the 'package' table in the shared database.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "package")
public class SubscriptionPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_package_id")
    private Long pkPackageId;

    @Column(name = "package_name")
    private String packageName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_type_id")
    private PackageType packageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_billing_cycle_id")
    private BillingCycle billingCycle;

    @Column(name = "description")
    private String description;

    @Column(name = "trial_days")
    private Integer trialDays = 14;

    @Column(name = "is_trial_available")
    private Boolean isTrialAvailable = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
