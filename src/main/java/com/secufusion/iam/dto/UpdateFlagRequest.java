package com.secufusion.iam.dto;

import lombok.Data;

@Data
public class UpdateFlagRequest {
    boolean enabled;
    String description;
}
