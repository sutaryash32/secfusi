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
public class PolicyGroupAssignmentDto {
    private String policyId;
    private String policyType;         // "BROWSER", "NETWORK", "EXTENSION"
    private List<GroupInfoDto> eventGroups;
}
