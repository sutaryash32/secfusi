package com.secufusion.iam.openFeatureService.dto;

import lombok.Data;

@Data
public class UpdateFlagRequest {
    boolean enabled;
    String description;
}
