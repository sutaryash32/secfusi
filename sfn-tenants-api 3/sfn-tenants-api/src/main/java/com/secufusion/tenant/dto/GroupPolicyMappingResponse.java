package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPolicyMappingResponse {
    private String assignmentType;
    private List<PolicyGroupAssignmentDto> assignments;
    private int totalAssignmentsCreated;
}
