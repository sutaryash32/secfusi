package com.secufusion.iam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FeatureResponse {

    private Long featureId;
    private String featureName;
    private String description;

    private String featureScope;
    private String featureType;

    private Boolean isActive;
    private LocalDateTime lastModifiedTimestamp;
}
