package com.secufusion.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDTO {

    private String incidentId;
    private String incidentNumber;
    private String title;
    private String status;
    private String priority;
    private String category;
    private String assignedTo;
    private String assignedToName;
    private Integer eventCount;
    private String source;
    private String createdAt;
    private String updatedAt;
    private String resolvedAt;
    private String closedAt;
    private String mergedIntoId;
    private String mergedIntoNumber;
    private Boolean isMerged;
}
