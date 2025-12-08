package com.secufusion.iam.openFeatureService.dto;

import lombok.Data;

@Data
public class CreateApiRequest {
    String apiKey;
    String path;
    String description;
}
