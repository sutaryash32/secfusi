package com.secufusion.iam.openFeatureService.dto;

import lombok.Data;

@Data
public class CreateFlagRequest {
    String flagKey;
    boolean enabled;
    String description;
}
