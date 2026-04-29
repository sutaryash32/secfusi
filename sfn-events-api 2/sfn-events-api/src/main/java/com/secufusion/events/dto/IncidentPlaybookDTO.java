package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentPlaybookDTO {

    private String playbookId;
    private String templateId;
    private String templateName;
    private String category;
    private Integer totalSteps;
    private Integer completedSteps;
    private Boolean isComplete;
    private List<IncidentPlaybookStepDTO> steps;
    private String createdAt;
}
