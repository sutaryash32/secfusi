package com.secufusion.iam.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "features")
@Data
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_feature_id")
    private Long pkFeatureID;

    @Column(name = "feature_name")
    private String featureName;

    @Column(name = "feature_code", unique = true, length = 100)
    private String featureCode;

    @Column(name = "description")
    private String description;

    @Column(name = "feature_scope")
    private String featureScope;

    @Column(name = "feature_type")
    private String featureType;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Addon feature fields
    @Column(name = "is_addon")
    private Boolean isAddon = false;

    @Column(name = "addon_monthly_price")
    private java.math.BigDecimal addonMonthlyPrice;

    @Column(name = "addon_trial_days")
    private Integer addonTrialDays = 7;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_feature_group_id")
    @JsonIgnore
    private FeatureGroup featureGroup;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @CreationTimestamp
    @Column(name = "created_timestamp")
    private LocalDateTime createdTimestamp;

    @UpdateTimestamp
    @Column(name = "last_modified_timestamp")
    private LocalDateTime lastModifiedTimestamp;
}
