package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feature_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeatureType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feature_type_id")
    private Long featureTypeID;

    @Column(name = "feature_type_name")
    private String featureTypeName;

    @Column(name = "feature_type_code")
    private String featureTypeCode;

    @Column(name = "is_active")
    private Boolean isActive;
}
