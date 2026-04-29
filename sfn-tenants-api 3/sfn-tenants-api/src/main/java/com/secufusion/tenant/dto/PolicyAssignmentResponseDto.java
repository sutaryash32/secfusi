package com.secufusion.tenant.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PolicyAssignmentResponseDto {

    private String assignmentId;

    private String policyType;

    private String policyId;

    private String policyName;

    private String azureResourceId;

    private String azureResourceName;

    private String assignmentType;

    private LocalDateTime assignedAt;
}