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
public class PackageFeatureMatrixResponse {

    private Long packageId;
    private String packageName;
    private String packageTypeName;
    private List<FeatureGroupMatrix> featureGroups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureGroupMatrix {
        private Long featureGroupId;
        private String featureGroupName;
        private String featureGroupCode;
        private Integer displayOrder;
        private List<FeatureAccessInfo> features;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureAccessInfo {
        private Long featureId;
        private String featureName;
        private String featureCode;
        private String accessLevel;
        private String accessLevelCode;
        private Integer accessLevelValue;
        private String retentionPeriod;
        private String retentionPeriodCode;
        private Integer retentionDays;
        private Boolean isEnabled;
    }
}
