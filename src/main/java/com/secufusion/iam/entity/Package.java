package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "package")
public class Package {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_package_id")
    private Long pkPackageId;

    @Column(name = "package_name")
    private String packageName;

    @ManyToOne
    @JoinColumn(name = "package_type_id")
    private PackageType packageType;

    @ManyToOne
    @JoinColumn(name = "fk_billing_cycle_id")
    private BillingCycle billingCycle;

    @Column(name = "description")
    private String description;

    @Column(name = "trial_days")
    private Integer trialDays = 14;

    @Column(name = "is_trial_available")
    private Boolean isTrialAvailable = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "package_feature_mapping",
            joinColumns = @JoinColumn(name = "fk_package_id", referencedColumnName = "pk_package_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_feature_id", referencedColumnName = "pk_feature_id")
    )
    private Set<Feature> features = new HashSet<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

}
