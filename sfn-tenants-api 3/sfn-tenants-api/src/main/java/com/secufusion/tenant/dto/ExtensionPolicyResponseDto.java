package com.secufusion.tenant.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExtensionPolicyResponseDto {

    private String policyId;
    private String tenantId;
    private String name;
    private String description;
    private String policyKey;
    private String version;
    private Boolean isActive;
    private String landingPageUrl;

    private ManagedExtensionResponseDto managedExtension;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
