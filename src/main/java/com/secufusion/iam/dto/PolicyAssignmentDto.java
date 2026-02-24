package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for PolicyAssignment entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyAssignmentDto {

    private String assignmentId;
    private String tenantId;
    private String groupId;
    private String groupName;
    private String assignmentType;
    private String resourceId;
    private String resourceName;
    private LocalDateTime assignedAt;
}
