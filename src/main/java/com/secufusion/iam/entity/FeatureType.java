package com.secufusion.iam.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FeatureTypes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeatureType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long featureTypeID;

    private String featureTypeName;
    private String featureTypeCode;
    private Boolean isActive;
}
