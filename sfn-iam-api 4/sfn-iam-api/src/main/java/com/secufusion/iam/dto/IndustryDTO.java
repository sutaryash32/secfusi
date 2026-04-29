package com.secufusion.iam.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndustryDTO {

    private Long industryId;
    private String industryName;
    private String industryCode;
    private Long parentIndustryId;
    private Boolean isActive;
    private UUID createdBy;
    private LocalDateTime createdTimestamp;
    private UUID lastModifiedBy;
    private LocalDateTime lastModifiedTimestamp;
}
