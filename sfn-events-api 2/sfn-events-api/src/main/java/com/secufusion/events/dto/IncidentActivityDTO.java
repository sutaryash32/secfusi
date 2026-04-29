package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentActivityDTO {

    private String activityId;
    private String action;
    private String description;
    private String performedBy;
    private String performedByName;
    private String performedAt;
    private String oldValue;
    private String newValue;
}
