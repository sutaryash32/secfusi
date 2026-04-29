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
public class PolicyMappingRequest {

    // The Policy we are linking to
    private String policyId;

    // The list of Azure resources (Roles/Groups) to link.
    // If linking one at a time, this list will contain just 1 element.
    private List<AzureAssignmentDto> assignments;
}