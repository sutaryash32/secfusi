package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentPlaybookStepDTO {

    private String stepId;
    private Integer stepNumber;
    private String title;
    private String description;
    private Boolean isRequired;
    private Boolean isCompleted;
    private String completedBy;
    private String completedByName;
    private String completedAt;
    private String notes;
}
