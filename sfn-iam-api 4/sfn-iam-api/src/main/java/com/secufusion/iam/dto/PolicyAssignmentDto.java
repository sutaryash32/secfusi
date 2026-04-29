package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for PolicyAssignment entity with policy details
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

    // Policy details
    private PolicyDetailsDto browserPolicy;
    private PolicyDetailsDto networkPolicy;
    private PolicyDetailsDto extensionPolicy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyDetailsDto {
        private String policyId;
        private String name;
        private String description;
        private String policyType;
        private String policyKey;
        private String version;
        private Boolean isActive;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
