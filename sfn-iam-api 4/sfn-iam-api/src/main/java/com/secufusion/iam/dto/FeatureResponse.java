package com.secufusion.iam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FeatureResponse {

    private Long featureId;
    private String featureName;
    private String featureCode;
    private String description;

    private String featureScope;
    private String featureType;

    private Long featureGroupId;
    private String featureGroupName;

    private Boolean isActive;
    private Boolean isAddon;
    private java.math.BigDecimal addonMonthlyPrice;
    private Integer addonTrialDays;

    private LocalDateTime lastModifiedTimestamp;
}
