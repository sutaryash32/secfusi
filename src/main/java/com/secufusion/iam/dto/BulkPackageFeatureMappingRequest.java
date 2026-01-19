package com.secufusion.iam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkPackageFeatureMappingRequest {

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @NotEmpty(message = "At least one feature mapping is required")
    @Valid
    private List<FeatureMappingItem> mappings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureMappingItem {

        @NotNull(message = "Feature group ID is required")
        private Long featureGroupId;

        @NotNull(message = "Feature ID is required")
        private Long featureId;

        @NotNull(message = "Access level ID is required")
        private Long accessLevelId;

        private Long retentionPeriodId;

        private Boolean isEnabled = true;

        private Map<String, Object> customConfig;
    }
}
