package com.secufusion.tenant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WatermarkingRequest {

    private boolean enabled;

    @JsonProperty("image_url")
    private String imageUrl;
}