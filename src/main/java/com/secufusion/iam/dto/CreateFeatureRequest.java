package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class CreateFeatureRequest {

    private String featureName;
    private String featureCode;
    private String description;
    private Long featureScopeId;
    private Long featureTypeId;
    private Long featureGroupId;
    private Boolean isAddon;
    private java.math.BigDecimal addonMonthlyPrice;
    private Integer addonTrialDays;
    private Long createdBy;
}
