package com.secufusion.tenant.mapper;

import com.secufusion.tenant.dto.PolicyAssignmentResponseDto;
import com.secufusion.tenant.entity.PolicyAssignment;
import org.springframework.stereotype.Component;

@Component
public class PolicyAssignmentMapper {

    public PolicyAssignmentResponseDto map(PolicyAssignment entity) {

        PolicyAssignmentResponseDto dto = new PolicyAssignmentResponseDto();

        dto.setAssignmentId(entity.getId());
        dto.setAzureResourceId(entity.getAzureResourceId());
        dto.setAzureResourceName(entity.getAzureResourceName());
        dto.setAssignmentType(entity.getAssignmentType());
        dto.setAssignedAt(entity.getAssignedAt());

        if (entity.getBrowserPolicy() != null) {
            dto.setPolicyType("BROWSER");
            dto.setPolicyId(entity.getBrowserPolicy().getPkBrowserPolicyId());
            dto.setPolicyName(entity.getBrowserPolicy().getName());
        }

        else if (entity.getNetworkPolicy() != null) {
            dto.setPolicyType("NETWORK");
            dto.setPolicyId(entity.getNetworkPolicy().getPkNetworkPolicyId());
            dto.setPolicyName(entity.getNetworkPolicy().getName());
        }

        else if (entity.getExtensionPolicy() != null) {
            dto.setPolicyType("EXTENSION");
            dto.setPolicyId(entity.getExtensionPolicy().getPkExtensionPolicyId());
            dto.setPolicyName(entity.getExtensionPolicy().getName());
        }

        return dto;
    }
}