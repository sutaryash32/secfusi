package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageFeatureMappingResponse {

    private Long mappingId;
    private Long packageId;
    private String packageName;
    private Long featureGroupId;
    private String featureGroupName;
    private String featureGroupCode;
    private Long featureId;
    private String featureName;
    private String featureCode;
    private Long accessLevelId;
    private String accessLevelName;
    private String accessLevelCode;
    private Integer accessLevelValue;
    private Long retentionPeriodId;
    private String retentionPeriodName;
    private String retentionPeriodCode;
    private Integer retentionDays;
    private Boolean isEnabled;
    private Map<String, Object> customConfig;
    private Instant createdAt;
    private Instant updatedAt;
}
