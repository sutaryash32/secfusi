package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class CreateFeatureRequest {

    private String featureName;
    private String featureCode;
    private String description;

    // Accept either ID or name for scope
    private Long featureScopeId;
    private String featureScope;

    // Accept either ID or name for type
    private Long featureTypeId;
    private String featureType;

    private Long featureGroupId;
    private Boolean isAddon;
    private java.math.BigDecimal addonMonthlyPrice;
    private Integer addonTrialDays;
    private Long createdBy;
}
