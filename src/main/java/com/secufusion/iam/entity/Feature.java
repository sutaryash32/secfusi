package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Features")
@Data
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pkFeatureID;

    private String featureName;

    @Column(name = "feature_code", unique = true, length = 100)
    private String featureCode;

    private String description;

    private String featureScope;

    private String featureType;

    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_feature_group_id")
    private FeatureGroup featureGroup;

    private Long createdBy;
    private Long lastModifiedBy;

    @CreationTimestamp
    private LocalDateTime createdTimestamp;

    @UpdateTimestamp
    private LocalDateTime lastModifiedTimestamp;
}
