package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantAddonFeatureResponse {

    private Long pkAddonId;

    private String tenantId;

    private String tenantName;

    private Long featureId;

    private String featureName;

    private String featureCode;

    private String featureGroupName;

    private Long accessLevelId;

    private String accessLevelName;

    private Integer accessLevelValue;

    private Long retentionPeriodId;

    private String retentionPeriodName;

    private Integer retentionDays;

    private Boolean isEnabled;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isTrial;

    private Boolean isExpired;

    private Map<String, Object> customConfig;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdBy;

    private String updatedBy;
}
