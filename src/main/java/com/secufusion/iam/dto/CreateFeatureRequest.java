package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class CreateFeatureRequest {

    private String featureName;
    private String description;
    private Long featureScopeId;
    private Long featureTypeId;
    private Long createdBy;
}
