package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class CreateApiRequest {
    String apiKey;
    String path;
    String description;
}
