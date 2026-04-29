package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkTenantAddonFeatureRequest {

    private String tenantId;

    private List<AddonFeatureItem> addons;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddonFeatureItem {
        private Long featureId;
        private String featureCode;
        private Long accessLevelId;
        private Long retentionPeriodId;
    }
}
