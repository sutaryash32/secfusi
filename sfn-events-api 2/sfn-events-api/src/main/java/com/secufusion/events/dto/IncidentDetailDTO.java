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
public class IncidentDetailDTO {

    // Identification
    private String incidentId;
    private String incidentNumber;
    private String title;
    private String description;

    // Classification
    private String status;
    private String priority;
    private String category;

    // Assignment
    private String assignedTo;
    private String assignedToName;
    private String assignedAt;

    // Resolution
    private String resolvedBy;
    private String resolvedByName;
    private String resolvedAt;
    private String resolutionNotes;
    private String rootCause;
    private String closedAt;

    // Source
    private String source;
    private String autoRuleName;

    // Audit
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;

    // Linked events
    private List<SecurityEventDTO> linkedEvents;
    private Integer eventCount;

    // Recent activity
    private List<IncidentActivityDTO> recentActivity;
    private long activityCount;
}
