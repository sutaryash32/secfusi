package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLevelResponse {

    private Long accessLevelId;
    private String levelName;
    private String levelCode;
    private Integer levelValue;
    private String description;
    private Boolean isActive;
}
