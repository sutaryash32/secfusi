package com.secufusion.iam.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_feature_group_id")
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
