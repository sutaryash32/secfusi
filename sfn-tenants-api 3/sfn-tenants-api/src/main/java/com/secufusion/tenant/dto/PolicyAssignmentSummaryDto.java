package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Summary DTO for policy assignments.
 * Used to show assignment details in policy response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyAssignmentSummaryDto {

    private String assignmentId;

    private String azureResourceId;

    private String azureResourceName;

    /**
     * Type of assignment: "ROLE" or "GROUP"
     */
    private String assignmentType;

    private LocalDateTime assignedAt;
}
