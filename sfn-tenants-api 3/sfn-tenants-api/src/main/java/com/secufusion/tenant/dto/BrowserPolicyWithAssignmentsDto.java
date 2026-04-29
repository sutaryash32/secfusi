package com.secufusion.tenant.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.secufusion.tenant.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for BrowserPolicy with assignment status.
 * Includes policy details and list of Azure role/group assignments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowserPolicyWithAssignmentsDto {

    // ==================== Policy Basic Info ====================
    private String pkBrowserPolicyId;
    private String name;
    private String description;
    private String policyType;
    private JsonNode urlRestriction;

    // ==================== Versioning ====================
    private String policyKey;
    private String version;
    private boolean isActive;

    // ==================== Tenant ====================
    private String fkTenantId;

    // ==================== Timestamps ====================
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== Nested Entities ====================
    private Dlp dlp;
    private ComplianceRules complianceRules;
    private Homepage homepage;
    private String landingPageUrl;
    private String landingPageId;

    // ==================== Assignment Status ====================
    /**
     * Total number of assignments for this policy
     */
    private int totalAssignments;

    /**
     * Number of ROLE type assignments
     */
    private int roleAssignments;

    /**
     * Number of GROUP type assignments
     */
    private int groupAssignments;

    /**
     * Whether this policy has any assignments
     */
    private boolean assigned;

    /**
     * List of assignment details
     */
    @Builder.Default
    private List<PolicyAssignmentSummaryDto> assignments = new ArrayList<>();

    // ==================== Static Factory Method ====================

    /**
     * Creates DTO from BrowserPolicy entity with assignment details.
     *
     * @param policy      The BrowserPolicy entity
     * @param assignments List of PolicyAssignment entities
     * @return BrowserPolicyWithAssignmentsDto
     */
    public static BrowserPolicyWithAssignmentsDto fromEntity(
            BrowserPolicy policy,
            List<PolicyAssignment> assignments) {

        List<PolicyAssignmentSummaryDto> assignmentSummaries = new ArrayList<>();
        int roleCount = 0;
        int groupCount = 0;

        if (assignments != null && !assignments.isEmpty()) {
            for (PolicyAssignment assignment : assignments) {
                assignmentSummaries.add(PolicyAssignmentSummaryDto.builder()
                        .assignmentId(assignment.getId())
                        .azureResourceId(assignment.getAzureResourceId())
                        .azureResourceName(assignment.getAzureResourceName())
                        .assignmentType(assignment.getAssignmentType())
                        .assignedAt(assignment.getAssignedAt())
                        .build());

                if ("ROLE".equalsIgnoreCase(assignment.getAssignmentType())) {
                    roleCount++;
                } else if ("GROUP".equalsIgnoreCase(assignment.getAssignmentType())) {
                    groupCount++;
                }
            }
        }

        return BrowserPolicyWithAssignmentsDto.builder()
                .pkBrowserPolicyId(policy.getPkBrowserPolicyId())
                .name(policy.getName())
                .description(policy.getDescription())
                .policyType(policy.getPolicyType())
                .urlRestriction(policy.getUrlRestriction())
                .policyKey(policy.getPolicyKey())
                .version(policy.getVersion())
                .isActive(policy.isActive())
                .fkTenantId(policy.getFkTenantId())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .dlp(policy.getDlp())
                .complianceRules(policy.getComplianceRules())
                .homepage(policy.getHomepage())
                .landingPageUrl(policy.getLandingPageUrl())
                .landingPageId(policy.getLandingPageId())
                .totalAssignments(assignmentSummaries.size())
                .roleAssignments(roleCount)
                .groupAssignments(groupCount)
                .assigned(!assignmentSummaries.isEmpty())
                .assignments(assignmentSummaries)
                .build();
    }

    /**
     * Creates DTO from BrowserPolicy entity without assignments (for quick lookups).
     *
     * @param policy The BrowserPolicy entity
     * @return BrowserPolicyWithAssignmentsDto with empty assignments
     */
    public static BrowserPolicyWithAssignmentsDto fromEntityWithoutAssignments(BrowserPolicy policy) {
        return fromEntity(policy, null);
    }
}
