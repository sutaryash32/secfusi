package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPeriodResponse {

    private Long retentionPeriodId;
    private String periodName;
    private String periodCode;
    private Integer periodDays;
    private String description;
    private Boolean isActive;
}
