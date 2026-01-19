package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantAddonFeatureRequest {

    private String tenantId;

    private Long featureId;

    private String featureCode;

    private Long accessLevelId;

    private Long retentionPeriodId;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isTrial = false;

    private Map<String, Object> customConfig;
}
