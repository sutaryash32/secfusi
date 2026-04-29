package com.secufusion.tenant.dto;

import com.secufusion.tenant.entity.ExtensionPolicy;
import com.secufusion.tenant.entity.PolicyAssignment;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExtensionPolicyWithAssignmentsDto {

    private ExtensionPolicy policy;
    private List<PolicyAssignment> assignments;

    public static ExtensionPolicyWithAssignmentsDto fromEntity(
            ExtensionPolicy policy,
            List<PolicyAssignment> assignments
    ) {
        return new ExtensionPolicyWithAssignmentsDto(policy, assignments);
    }
}
