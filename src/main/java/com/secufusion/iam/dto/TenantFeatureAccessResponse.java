package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantFeatureAccessResponse {

    private String tenantId;
    private String tenantName;
    private Long packageId;
    private String packageName;
    private List<FeatureAccess> features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureAccess {
        private String featureCode;
        private String featureName;
        private String featureGroup;
        private String featureGroupCode;
        private String accessLevel;
        private String accessLevelCode;
        private Integer accessLevelValue;
        private Boolean hasAccess;
        private String retentionPeriod;
        private Integer retentionDays;
    }
}
